package com.tengames.notification;

import com.tengames.auth.email.EmailSender;
import com.tengames.gameweek.Gameweek;
import com.tengames.gameweek.GameweekFixture;
import com.tengames.gameweek.GameweekFixtureRepository;
import com.tengames.gameweek.GameweekRepository;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tells players a round has opened, and nudges the ones who haven't played
 * before the first kickoff.
 *
 * <p>Two rules shape everything here. A player is never mailed twice about the
 * same round — the unique index on {@code notifications} enforces it, and each
 * send writes its row inside its own transaction so a provider failure rolls
 * back that row alone and the mail is retried on the next tick. And the daily
 * budget is respected: hitting the provider's limit means losing mail
 * silently, so we stop before it and resume later.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** How close a kickoff must be for a reminder to be worth an inbox. */
    private static final Duration REMINDER_LEAD = Duration.ofHours(12);

    private static final DateTimeFormatter DEADLINE =
            DateTimeFormatter.ofPattern("EEEE d MMMM 'at' HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Europe/Paris"));
    private static final DateTimeFormatter KICKOFF =
            DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Europe/Paris"));

    private final GameweekRepository gameweeks;
    private final GameweekFixtureRepository fixtures;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final UnsubscribeTokens tokens;
    private final NotificationProperties properties;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private final TransactionTemplate sendTx;
    private final Clock clock;

    public NotificationService(GameweekRepository gameweeks, GameweekFixtureRepository fixtures,
                               NotificationRepository notifications, UserRepository users,
                               EmailSender emailSender, UnsubscribeTokens tokens,
                               NotificationProperties properties,
                               org.springframework.jdbc.core.simple.JdbcClient jdbc,
                               PlatformTransactionManager txManager, Clock clock) {
        this.gameweeks = gameweeks;
        this.fixtures = fixtures;
        this.notifications = notifications;
        this.users = users;
        this.emailSender = emailSender;
        this.tokens = tokens;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
        this.sendTx = new TransactionTemplate(txManager);
        this.sendTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Announces every published round nobody has been told about yet.
     *
     * <p>Rounds whose first kickoff has passed are skipped: announcing a round
     * that can no longer be played in full is noise, and it is also what keeps
     * this from mailing everyone about old rounds the first time it runs.
     */
    public int notifyOpenedRounds() {
        int sent = 0;
        for (Gameweek gameweek : gameweeks.findByStatus(Gameweek.Status.PUBLISHED)) {
            List<GameweekFixture> card = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweek.getId());
            if (card.isEmpty() || !clock.instant().isBefore(firstKickoff(card))) {
                continue;
            }
            Set<Long> already = Set.copyOf(
                    notifications.userIdsNotified(gameweek.getId(), Notification.Kind.ROUND_OPENED));
            for (User user : recipients()) {
                if (already.contains(user.getId())) {
                    continue;
                }
                if (!withinDailyBudget()) {
                    return sent;
                }
                if (send(user, gameweek, Notification.Kind.ROUND_OPENED,
                        openedSubject(gameweek), openedBody(user, gameweek, card))) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /**
     * Reminds players whose card is still incomplete, once the first kickoff
     * is close. Players who have already filled everything in are left alone —
     * that is what makes the reminder worth reading rather than ignoring.
     */
    public int sendReminders() {
        int sent = 0;
        Instant now = clock.instant();
        for (Gameweek gameweek : gameweeks.findByStatus(Gameweek.Status.PUBLISHED)) {
            List<GameweekFixture> card = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweek.getId());
            if (card.isEmpty()) {
                continue;
            }
            Instant deadline = firstKickoff(card);
            if (!now.isBefore(deadline) || now.isBefore(deadline.minus(REMINDER_LEAD))) {
                continue;
            }
            Set<Long> already = Set.copyOf(
                    notifications.userIdsNotified(gameweek.getId(), Notification.Kind.KICKOFF_REMINDER));
            for (User user : recipients()) {
                if (already.contains(user.getId())) {
                    continue;
                }
                int missing = card.size() - predictionCount(user.getId(), gameweek.getId());
                if (missing <= 0) {
                    continue;
                }
                if (!withinDailyBudget()) {
                    return sent;
                }
                if (send(user, gameweek, Notification.Kind.KICKOFF_REMINDER,
                        "Round %d closes soon".formatted(gameweek.getWeekIndex()),
                        reminderBody(user, gameweek, missing, deadline))) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /**
     * Honours an unsubscribe link. Idempotent: clicking twice is not an error,
     * and a link for an account that no longer exists is a success too — the
     * person asked not to be mailed, and they will not be.
     *
     * <p>A tampered id and a tampered token give the same answer, so this
     * cannot be used to find out which addresses have accounts.
     */
    public void unsubscribe(String publicId, String token) {
        UUID id;
        try {
            id = UUID.fromString(publicId);
        } catch (IllegalArgumentException e) {
            throw invalidLink();
        }
        if (!tokens.isValid(id, token)) {
            throw invalidLink();
        }
        // an explicit transaction rather than @Transactional: this class is
        // also used directly, and a method that silently stops persisting
        // anything when it isn't called through the proxy is a trap
        sendTx.executeWithoutResult(status ->
                users.findByPublicId(id).ifPresent(user -> user.setNotifyEmail(false)));
    }

    private static ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "That unsubscribe link is not valid");
    }

    /**
     * Records the send and performs it, in one transaction of its own.
     *
     * <p>Order matters: the row is written first so a concurrent run cannot
     * send the same mail, and the send happens inside the same transaction so
     * a provider failure takes the row with it and the mail is retried rather
     * than lost. A failure here must never abort the rest of the batch.
     */
    private boolean send(User user, Gameweek gameweek, Notification.Kind kind, String subject, String body) {
        try {
            sendTx.executeWithoutResult(status -> {
                notifications.saveAndFlush(new Notification(user.getId(), gameweek.getId(), kind, clock.instant()));
                emailSender.send(user.getEmail(), subject, body);
            });
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // the unique index did its job: someone else already sent this one
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not notify user {} about gameweek {} ({}): {}",
                    user.getId(), gameweek.getId(), kind, e.getMessage());
            return false;
        }
    }

    /** Verified players who still want mail. Unverified addresses may not even exist. */
    private List<User> recipients() {
        return users.findByEmailVerifiedTrueAndNotifyEmailTrue();
    }

    /**
     * Each send commits its own row, so the count in the database already
     * includes what this run has sent — adding a local tally would double it.
     */
    private boolean withinDailyBudget() {
        if (notifications.countBySentAtAfter(clock.instant().minus(Duration.ofDays(1)))
            < properties.dailyBudget()) {
            return true;
        }
        log.info("Daily email budget of {} reached; the rest will go out on a later run",
                properties.dailyBudget());
        return false;
    }

    private int predictionCount(long userId, long gameweekId) {
        return jdbc.sql("""
                        select count(*) from predictions p
                        join gameweek_fixtures f on f.id = p.gameweek_fixture_id
                        where f.gameweek_id = :gameweekId and p.user_id = :userId
                        """)
                .param("gameweekId", gameweekId)
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    private static Instant firstKickoff(List<GameweekFixture> card) {
        return card.getFirst().getMatch().getKickoffUtc();
    }

    private static String openedSubject(Gameweek gameweek) {
        return gameweek.isCountsTowardsTable()
                ? "Round %d is open".formatted(gameweek.getWeekIndex())
                : "Warm-up round is open — no points at stake";
    }

    private String openedBody(User user, Gameweek gameweek, List<GameweekFixture> card) {
        StringBuilder body = new StringBuilder("Hi %s,\n\n".formatted(user.getDisplayName()));
        body.append(gameweek.isCountsTowardsTable()
                ? "Round %d is open. Ten matches to call:\n\n".formatted(gameweek.getWeekIndex())
                : "The warm-up round is open — it is scored, but it does not count "
                  + "towards the table. Ten matches to call:\n\n");
        for (GameweekFixture fixture : card) {
            body.append("  %s — %s  (%s)\n".formatted(
                    fixture.getMatch().getHomeTeam().getName(),
                    fixture.getMatch().getAwayTeam().getName(),
                    KICKOFF.format(fixture.getMatch().getKickoffUtc())));
        }
        body.append("\nPredictions close at the first kickoff, %s.\n\n%s\n"
                .formatted(DEADLINE.format(firstKickoff(card)), properties.baseUrl()));
        return body.append(footer(user)).toString();
    }

    private String reminderBody(User user, Gameweek gameweek, int missing, Instant deadline) {
        return """
                Hi %s,

                %s of your predictions for round %d are still missing, and the first
                match kicks off %s. A missing prediction scores nothing.

                %s
                """.formatted(user.getDisplayName(), missing, gameweek.getWeekIndex(),
                DEADLINE.format(deadline), properties.baseUrl()) + footer(user);
    }

    private String footer(User user) {
        return "\n—\nTo stop these emails: %s/unsubscribe?u=%s&t=%s\n".formatted(
                properties.baseUrl(), user.getPublicId(), tokens.tokenFor(user.getPublicId()));
    }
}
