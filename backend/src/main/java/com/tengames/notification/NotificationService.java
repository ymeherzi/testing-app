package com.tengames.notification;

import com.tengames.gameweek.Gameweek;
import com.tengames.gameweek.GameweekFixture;
import com.tengames.gameweek.GameweekFixtureRepository;
import com.tengames.gameweek.GameweekRepository;
import com.tengames.push.PushMessage;
import com.tengames.push.PushService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells players a round has opened, nudges the ones who haven't played before
 * the first kickoff, and announces the start of the season.
 *
 * <p>Everything goes out as a web push notification. Email is reserved for
 * account business — signup codes, new-device codes, password resets — and
 * this class never touches it.
 *
 * <p>A player is never told the same thing twice: the unique index on
 * {@code notifications} enforces it, and each send writes its row inside its
 * own transaction, so a push that fails everywhere rolls that row back and is
 * retried on the next tick instead of being lost.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** How close a kickoff must be for a reminder to be worth interrupting someone. */
    private static final Duration REMINDER_LEAD = Duration.ofHours(12);

    private static final DateTimeFormatter DEADLINE =
            DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.FRENCH).withZone(ZoneId.of("Europe/Paris"));

    private final GameweekRepository gameweeks;
    private final GameweekFixtureRepository fixtures;
    private final NotificationRepository notifications;
    private final PushService push;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private final TransactionTemplate sendTx;
    private final Clock clock;

    public NotificationService(GameweekRepository gameweeks, GameweekFixtureRepository fixtures,
                               NotificationRepository notifications, PushService push,
                               org.springframework.jdbc.core.simple.JdbcClient jdbc,
                               PlatformTransactionManager txManager, Clock clock) {
        this.gameweeks = gameweeks;
        this.fixtures = fixtures;
        this.notifications = notifications;
        this.push = push;
        this.jdbc = jdbc;
        this.clock = clock;
        this.sendTx = new TransactionTemplate(txManager);
        this.sendTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Announces every published round nobody has been told about yet.
     *
     * <p>Rounds whose first kickoff has passed are skipped: a round that can no
     * longer be played in full is not news, and the same rule stops the first
     * run after a deployment from announcing the whole history.
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
            for (Long userId : push.subscribedUserIds()) {
                if (already.contains(userId)) {
                    continue;
                }
                PushMessage message = new PushMessage(
                        gameweek.isCountsTowardsTable()
                                ? "Journée %d ouverte".formatted(gameweek.getWeekIndex())
                                : "Journée blanche ouverte",
                        "%d matchs à pronostiquer. Clôture %s."
                                .formatted(card.size(), DEADLINE.format(firstKickoff(card))),
                        "/", "gameweek-" + gameweek.getId());
                if (send(userId, gameweek.getId(), Notification.Kind.ROUND_OPENED, message)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /**
     * Reminds players whose card is still incomplete, once the first kickoff is
     * close. Players who have already filled everything in are left alone —
     * that is what keeps the reminder worth reading rather than muting.
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
            for (Long userId : push.subscribedUserIds()) {
                if (already.contains(userId)) {
                    continue;
                }
                int missing = card.size() - predictionCount(userId, gameweek.getId());
                if (missing <= 0) {
                    continue;
                }
                PushMessage message = new PushMessage(
                        "Journée %d se clôture bientôt".formatted(gameweek.getWeekIndex()),
                        missing == 1
                                ? "Il te reste 1 prono à saisir avant %s.".formatted(DEADLINE.format(deadline))
                                : "Il te reste %d pronos à saisir avant %s."
                                        .formatted(missing, DEADLINE.format(deadline)),
                        "/", "gameweek-" + gameweek.getId());
                if (send(userId, gameweek.getId(), Notification.Kind.KICKOFF_REMINDER, message)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /**
     * The one-off "the season starts now" announcement, sent by the admin.
     *
     * <p>Belongs to no round, so it is deduplicated by kind alone: pressing the
     * button twice reaches nobody twice.
     */
    public int announceSeasonLaunch(String title, String body) {
        int sent = 0;
        Set<Long> already = Set.copyOf(notifications.userIdsAnnounced(Notification.Kind.SEASON_LAUNCH));
        for (Long userId : push.subscribedUserIds()) {
            if (already.contains(userId)) {
                continue;
            }
            if (send(userId, null, Notification.Kind.SEASON_LAUNCH, new PushMessage(title, body, "/", "launch"))) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * Records the notification and delivers it, in one transaction of its own.
     *
     * <p>The row is written first so a concurrent run cannot send the same
     * thing, and delivery happens inside the same transaction so a failure
     * takes the row with it and the notification is retried. One player's
     * failure must never abort the batch.
     */
    private boolean send(Long userId, Long gameweekId, Notification.Kind kind, PushMessage message) {
        try {
            return Boolean.TRUE.equals(sendTx.execute(status -> {
                notifications.saveAndFlush(new Notification(userId, gameweekId, kind, clock.instant()));
                if (!push.sendToUser(userId, message)) {
                    // no device took it; forget the row so the next tick tries again
                    status.setRollbackOnly();
                    return false;
                }
                return true;
            }));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // the unique index did its job: someone else got there first
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not notify user {} ({}): {}", userId, kind, e.getMessage());
            return false;
        }
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
}
