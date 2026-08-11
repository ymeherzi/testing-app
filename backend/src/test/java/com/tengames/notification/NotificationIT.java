package com.tengames.notification;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.email.EmailSender;
import com.tengames.auth.email.MailDeliveryException;
import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.catalog.Team;
import com.tengames.catalog.TeamRepository;
import com.tengames.gameweek.Gameweek;
import com.tengames.gameweek.GameweekDtos.GameweekView;
import com.tengames.gameweek.GameweekFixtureRepository;
import com.tengames.gameweek.GameweekRepository;
import com.tengames.gameweek.GameweekService;
import com.tengames.prediction.PredictionService;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A round that nobody is told about may as well not be open. The dangerous
 * failures here are the quiet ones: mailing the same person twice, mailing
 * someone who asked to be left alone, or burning the provider's daily
 * allowance and losing the rest of the batch without a trace.
 *
 * <p>These tests share a database with every other suite, so each assertion is
 * about a specific player and a specific round rather than about totals.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, NotificationIT.Fixtures.class})
@ActiveProfiles("test")
class NotificationIT {

    static class CapturingMailSender implements EmailSender {
        final List<String> recipients = new CopyOnWriteArrayList<>();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        volatile String failFor;

        @Override
        public void send(String to, String subject, String body) {
            if (to.equals(failFor)) {
                throw new MailDeliveryException("provider refused " + to, null);
            }
            recipients.add(to);
            bodies.add(body);
        }
    }

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    @Autowired
    private CapturingMailSender mail;

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private GameweekRepository gameweeks;

    @Autowired
    private GameweekFixtureRepository fixtures;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private PredictionService predictions;

    @Autowired
    private UserRepository users;

    @Autowired
    private CompetitionRepository competitions;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private UnsubscribeTokens tokens;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        mail.recipients.clear();
        mail.bodies.clear();
        mail.failFor = null;
        alice = verifiedPlayer("alice");
        bob = verifiedPlayer("bob");
    }

    private User verifiedPlayer(String name) {
        User user = new User("%s-%s@example.com".formatted(name, UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), name, "TN", null);
        user.markEmailVerified();
        return users.save(user);
    }

    /** A service with a budget of our choosing; everything else is the real thing. */
    private NotificationService serviceWithBudget(int budget) {
        return new NotificationService(gameweeks, fixtures, notifications, users, mail, tokens,
                new NotificationProperties("https://tengames.app/", budget), jdbc, txManager, clock);
    }

    /** Generous enough that other suites' data cannot exhaust it mid-test. */
    private NotificationService service() {
        return serviceWithBudget(100_000);
    }

    /**
     * A published round whose matches kick off at the given offset.
     *
     * <p>The window is pushed back into the past on purpose: a window around
     * "now" would make this round the current one for every other suite sharing
     * this database. Announcing depends on the kickoff, not on the window.
     */
    private long publishedRound(String season, Duration untilKickoff, int size) {
        Instant now = clock.instant();
        Competition competition = competitions.findByCode("NOTIF")
                .orElseGet(() -> competitions.save(new Competition("NOTIF", "Notification Cup")));
        List<Long> matchIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String ref = "notif:%s:%d".formatted(season, i);
            Team home = teams.save(new Team("Home " + ref, "HOM", null, ref + ":h"));
            Team away = teams.save(new Team("Away " + ref, "AWY", null, ref + ":a"));
            matchIds.add(matches.save(new Match(competition, home, away,
                    now.plus(untilKickoff).plus(Duration.ofMinutes(i)), MatchStatus.SCHEDULED, ref)).getId());
        }
        GameweekView draft = gameweekService.createDraft(season, 1, Gameweek.Type.WEEKEND,
                now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(59)), true);
        gameweekService.setFixtures(draft.id(), matchIds);
        // setFixtures derives the window from the kickoffs, which would drag it
        // back around "now"; put it back where other suites cannot see it
        gameweekService.rescheduleDraft(draft.id(), now.minus(Duration.ofDays(60)),
                now.minus(Duration.ofDays(59)), true);
        return gameweekService.publish(draft.id()).id();
    }

    private boolean wasNotified(User user, long gameweekId, Notification.Kind kind) {
        return notifications.existsByUserIdAndGameweekIdAndKind(user.getId(), gameweekId, kind);
    }

    /**
     * The email this player received about this round.
     *
     * <p>Rounds published by earlier tests are still open, so a player may
     * legitimately receive several emails in one run; picking simply the first
     * one would read the wrong round's body.
     */
    private String bodySentTo(User user, String round) {
        for (int i = 0; i < mail.recipients.size(); i++) {
            if (mail.recipients.get(i).equals(user.getEmail()) && mail.bodies.get(i).contains(round)) {
                return mail.bodies.get(i);
            }
        }
        throw new AssertionError("no email about round %s was sent to %s".formatted(round, user.getEmail()));
    }

    @Test
    void everyPlayerIsToldOnceWhenARoundOpens() {
        long round = publishedRound("9970-01", Duration.ofDays(2), 3);

        service().notifyOpenedRounds();

        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isTrue();
        assertThat(wasNotified(bob, round, Notification.Kind.ROUND_OPENED)).isTrue();
        assertThat(mail.recipients).contains(alice.getEmail(), bob.getEmail());

        // the job runs every quarter of an hour: a second pass must be silent
        mail.recipients.clear();
        service().notifyOpenedRounds();
        service().notifyOpenedRounds();
        assertThat(mail.recipients).doesNotContain(alice.getEmail(), bob.getEmail());
    }

    @Test
    void theAnnouncementCarriesTheCardTheDeadlineAndAWayOut() {
        long round = publishedRound("9970-02", Duration.ofDays(2), 3);

        service().notifyOpenedRounds();

        String firstMatch = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(round).getFirst()
                .getMatch().getHomeTeam().getName();
        String body = bodySentTo(alice, "9970-02");
        assertThat(body).contains(firstMatch).contains("Predictions close at the first kickoff");
        // a working unsubscribe link is what keeps us out of the spam folder —
        // and the base url must not end up doubled by the trailing slash
        assertThat(body).contains("https://tengames.app/unsubscribe?u=" + alice.getPublicId())
                .doesNotContain("tengames.app//");
    }

    @Test
    void aPlayerWhoUnsubscribedIsNeverMailedAgain() {
        String token = tokens.tokenFor(alice.getPublicId());
        service().unsubscribe(alice.getPublicId().toString(), token);

        long round = publishedRound("9970-03", Duration.ofDays(2), 3);
        service().notifyOpenedRounds();

        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isFalse();
        assertThat(wasNotified(bob, round, Notification.Kind.ROUND_OPENED)).isTrue();
        // clicking the link twice is not an error
        service().unsubscribe(alice.getPublicId().toString(), token);
    }

    @Test
    void anUnsubscribeLinkCannotBeForged() {
        NotificationService service = service();
        String bobsToken = tokens.tokenFor(bob.getPublicId());

        assertThatThrownBy(() -> service.unsubscribe(alice.getPublicId().toString(), bobsToken))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.unsubscribe(alice.getPublicId().toString(), "nonsense"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.unsubscribe("not-a-uuid", bobsToken))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(users.findById(alice.getId()).orElseThrow().isNotifyEmail()).isTrue();
    }

    @Test
    void aRoundThatHasAlreadyStartedIsNotAnnounced() {
        // otherwise the first run after a deployment mails everyone about
        // every round the app has ever published
        long round = publishedRound("9970-04", Duration.ofDays(-1), 3);

        service().notifyOpenedRounds();

        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isFalse();
    }

    @Test
    void onlyPlayersWithAnUnfinishedCardAreReminded() {
        long round = publishedRound("9970-05", Duration.ofHours(3), 2);
        for (var fixture : fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(round)) {
            predictions.upsert(alice.getId(), round, fixture.getId(), 1, 0);
        }

        service().sendReminders();

        assertThat(wasNotified(alice, round, Notification.Kind.KICKOFF_REMINDER)).isFalse();
        assertThat(wasNotified(bob, round, Notification.Kind.KICKOFF_REMINDER)).isTrue();
        assertThat(bodySentTo(bob, "still missing")).contains("2 of your predictions");
    }

    @Test
    void aReminderWaitsUntilTheKickoffIsClose() {
        long round = publishedRound("9970-06", Duration.ofDays(5), 2);

        service().sendReminders();

        assertThat(wasNotified(bob, round, Notification.Kind.KICKOFF_REMINDER)).isFalse();
    }

    @Test
    void theDailyBudgetStopsTheBatchInsteadOfLosingMail() {
        long round = publishedRound("9970-07", Duration.ofDays(2), 2);
        long alreadySentToday = notifications.countBySentAtAfter(clock.instant().minus(Duration.ofDays(1)));

        int sent = serviceWithBudget((int) alreadySentToday + 1).notifyOpenedRounds();

        // exactly one more email may leave, whoever it belongs to
        assertThat(sent).isEqualTo(1);
        assertThat(notifications.countBySentAtAfter(clock.instant().minus(Duration.ofDays(1))))
                .isEqualTo(alreadySentToday + 1);

        // and nothing is lost: with room again, the rest goes out
        service().notifyOpenedRounds();
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isTrue();
        assertThat(wasNotified(bob, round, Notification.Kind.ROUND_OPENED)).isTrue();
    }

    @Test
    void aFailedSendIsRetriedRatherThanRecordedAsDone() {
        long round = publishedRound("9970-08", Duration.ofDays(2), 2);
        mail.failFor = alice.getEmail();

        service().notifyOpenedRounds();

        // the row must not survive a failed send, or the email is lost forever
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isFalse();
        // and one bad address must not take the rest of the batch with it
        assertThat(wasNotified(bob, round, Notification.Kind.ROUND_OPENED)).isTrue();

        mail.failFor = null;
        service().notifyOpenedRounds();
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isTrue();
    }
}
