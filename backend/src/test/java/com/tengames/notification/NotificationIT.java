package com.tengames.notification;

import com.tengames.TestcontainersConfiguration;
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
import com.tengames.gameweek.GameweekService;
import com.tengames.prediction.PredictionService;
import com.tengames.push.PushMessage;
import com.tengames.push.PushSender;
import com.tengames.push.PushService;
import com.tengames.push.PushSubscription;
import com.tengames.push.PushSubscriptionGoneException;
import com.tengames.push.PushSubscriptionRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A round nobody is told about may as well not be open. The dangerous failures
 * are the quiet ones: notifying the same person twice, notifying someone who
 * never asked, or recording a notification that was never delivered — which
 * would mean it is never retried.
 *
 * <p>This database is shared with every other suite, so each assertion is
 * about a specific player and a specific round rather than about totals.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, NotificationIT.Fixtures.class})
@ActiveProfiles("test")
class NotificationIT {

    /** Stands in for the browser's push service. */
    static class CapturingPushSender implements PushSender {
        record Delivery(Long userId, PushMessage message) {
        }

        final List<Delivery> delivered = new CopyOnWriteArrayList<>();
        volatile Long failFor;
        volatile Long goneFor;

        @Override
        public void send(PushSubscription subscription, PushMessage message) {
            if (subscription.getUserId().equals(goneFor)) {
                throw new PushSubscriptionGoneException("gone");
            }
            if (subscription.getUserId().equals(failFor)) {
                throw new IllegalStateException("push service is down");
            }
            delivered.add(new Delivery(subscription.getUserId(), message));
        }

        List<PushMessage> to(Long userId) {
            return delivered.stream().filter(d -> d.userId().equals(userId)).map(Delivery::message).toList();
        }
    }

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        CapturingPushSender capturingPushSender() {
            return new CapturingPushSender();
        }
    }

    @Autowired
    private CapturingPushSender pushes;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private PushService push;

    @Autowired
    private PushSubscriptionRepository subscriptions;

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private GameweekFixtureRepository fixtures;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        pushes.delivered.clear();
        pushes.failFor = null;
        pushes.goneFor = null;
        alice = subscribedPlayer("alice");
        bob = subscribedPlayer("bob");
    }

    private User subscribedPlayer(String name) {
        User user = users.save(new User("%s-%s@example.com".formatted(name, UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), name, "TN", null));
        push.subscribe(user.getId(), "https://push.example.net/" + UUID.randomUUID(),
                "p256dh-" + user.getId(), "auth-" + user.getId());
        return user;
    }

    /**
     * A published round whose matches kick off at the given offset.
     *
     * <p>The window is pushed back into the past on purpose: a window around
     * "now" would make this round the current one for every other suite sharing
     * this database. What is announced depends on the kickoff, not the window.
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
        // back around "now"; put it where other suites cannot see it
        gameweekService.rescheduleDraft(draft.id(), now.minus(Duration.ofDays(60)),
                now.minus(Duration.ofDays(59)), true);
        return gameweekService.publish(draft.id()).id();
    }

    private boolean wasNotified(User user, long gameweekId, Notification.Kind kind) {
        return notifications.existsByUserIdAndGameweekIdAndKind(user.getId(), gameweekId, kind);
    }

    private List<PushMessage> pushesAbout(User user, long gameweekId) {
        return pushes.to(user.getId()).stream().filter(m -> m.tag().equals("gameweek-" + gameweekId)).toList();
    }

    @Test
    void everySubscribedPlayerIsToldOnceWhenARoundOpens() {
        long round = publishedRound("9960-01", Duration.ofDays(2), 3);

        notificationService.notifyOpenedRounds();

        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isTrue();
        assertThat(wasNotified(bob, round, Notification.Kind.ROUND_OPENED)).isTrue();
        assertThat(pushesAbout(alice, round)).hasSize(1);

        // the job runs every quarter of an hour: later passes must be silent
        notificationService.notifyOpenedRounds();
        notificationService.notifyOpenedRounds();
        assertThat(pushesAbout(alice, round)).hasSize(1);
    }

    @Test
    void theAnnouncementSaysHowManyMatchesAndWhenItCloses() {
        long round = publishedRound("9960-02", Duration.ofDays(2), 3);

        notificationService.notifyOpenedRounds();

        PushMessage message = pushesAbout(alice, round).getFirst();
        assertThat(message.title()).contains("Journée");
        assertThat(message.body()).contains("3 matchs");
        // a notification that opens nothing is a dead end
        assertThat(message.url()).isEqualTo("/");
    }

    @Test
    void aPlayerWithNoDeviceIsNeverNotified() {
        User silent = users.save(new User("silent-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "Silent", "TN", null));
        long round = publishedRound("9960-03", Duration.ofDays(2), 3);

        notificationService.notifyOpenedRounds();

        // having a subscription is the consent; there is no other opt-in to check
        assertThat(wasNotified(silent, round, Notification.Kind.ROUND_OPENED)).isFalse();
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isTrue();
    }

    @Test
    void aRoundThatHasAlreadyStartedIsNotAnnounced() {
        // otherwise the first run after a deployment notifies everyone about
        // every round the app has ever published
        long round = publishedRound("9960-04", Duration.ofDays(-1), 3);

        notificationService.notifyOpenedRounds();

        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isFalse();
    }

    @Test
    void onlyPlayersWithAnUnfinishedCardAreReminded() {
        long round = publishedRound("9960-05", Duration.ofHours(3), 2);
        for (var fixture : fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(round)) {
            predictions.upsert(alice.getId(), round, fixture.getId(), 1, 0);
        }

        notificationService.sendReminders();

        assertThat(wasNotified(alice, round, Notification.Kind.KICKOFF_REMINDER)).isFalse();
        assertThat(wasNotified(bob, round, Notification.Kind.KICKOFF_REMINDER)).isTrue();
        assertThat(pushesAbout(bob, round).getFirst().body()).contains("2 pronos");
    }

    @Test
    void aReminderWaitsUntilTheKickoffIsClose() {
        long round = publishedRound("9960-06", Duration.ofDays(5), 2);

        notificationService.sendReminders();

        assertThat(wasNotified(bob, round, Notification.Kind.KICKOFF_REMINDER)).isFalse();
    }

    @Test
    void theSeasonLaunchIsAnnouncedOnceAndOnlyOnce() {
        notificationService.announceSeasonLaunch("La saison commence", "Journée 1 est ouverte");
        int secondPress = notificationService.announceSeasonLaunch("La saison commence", "encore");

        assertThat(notifications.existsByUserIdAndGameweekIdIsNullAndKind(
                alice.getId(), Notification.Kind.SEASON_LAUNCH)).isTrue();
        assertThat(pushes.to(alice.getId()).stream().filter(m -> m.tag().equals("launch"))).hasSize(1);
        // pressing the button again must reach nobody, not everybody
        assertThat(secondPress).isZero();
    }

    @Test
    void theAnnouncementSaysHowManyItCouldHaveReached() {
        // "sent to 0" means two very different things — nobody subscribed, or
        // everybody already told — so the screen is given both numbers
        var controller = new AdminNotificationController(notificationService, push, jdbc);

        var first = controller.announceLaunch(
                new AdminNotificationController.AnnounceRequest("Ça commence", "Journée 1 ouverte"));
        var again = controller.announceLaunch(
                new AdminNotificationController.AnnounceRequest("Ça commence", "encore"));

        assertThat(first.get("subscribers")).isPositive();
        assertThat(first.get("sent")).isPositive();
        assertThat(again.get("sent")).isZero();
        // the audience does not shrink just because everyone has been told
        assertThat(again.get("subscribers")).isEqualTo(first.get("subscribers"));
    }

    @Test
    void theAudienceIsNamed() {
        // a count answers "how many", never "who" — and "who" is the question
        // that follows every send
        var controller = new AdminNotificationController(notificationService, push, jdbc);
        User silent = users.save(new User("silent-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "silent", "TN", null));

        @SuppressWarnings("unchecked")
        var listeners = (List<AdminNotificationController.Listener>) controller.audience().get("listeners");

        assertThat(listeners).filteredOn(l -> l.id().equals(alice.getPublicId()))
                .singleElement()
                .satisfies(l -> assertThat(l.devices()).isEqualTo(1));
        // nobody hears without having said yes
        assertThat(listeners).noneMatch(l -> l.id().equals(silent.getPublicId()));
    }

    @Test
    void aNotificationThatCouldNotBeDeliveredIsRetried() {
        long round = publishedRound("9960-07", Duration.ofDays(2), 2);
        pushes.failFor = alice.getId();

        notificationService.notifyOpenedRounds();

        // recording a send that never happened would lose it for good
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isFalse();
        // and one broken device must not take the rest of the batch with it
        assertThat(wasNotified(bob, round, Notification.Kind.ROUND_OPENED)).isTrue();

        pushes.failFor = null;
        notificationService.notifyOpenedRounds();
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isTrue();
    }

    @Test
    void aDeadSubscriptionIsForgottenRatherThanRetriedForever() {
        long round = publishedRound("9960-08", Duration.ofDays(2), 2);
        pushes.goneFor = alice.getId();

        notificationService.notifyOpenedRounds();

        assertThat(subscriptions.findByUserId(alice.getId())).isEmpty();
        assertThat(wasNotified(alice, round, Notification.Kind.ROUND_OPENED)).isFalse();
    }
}
