package com.tengames.gameweek;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.catalog.Team;
import com.tengames.catalog.TeamRepository;
import com.tengames.prediction.PredictionRepository;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which round a player lands on.
 *
 * <p>Rounds are now prepared several weeks ahead, so more than one can be
 * published at a time. "The latest published round" then means the furthest
 * one, and publishing two at once sent everybody to the wrong one — past the
 * round they were in the middle of.
 *
 * <p>The clock is fixed to a year no other suite writes in: this question is
 * global by nature, and the database is shared.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OpenRoundIT {

    /**
     * Each test picks its own year. The rounds a test publishes stay in the
     * shared database, and "which round is open" is a question about the whole
     * calendar: a neighbour's open round would answer it.
     */
    private static Instant year(int year) {
        return Instant.parse("%d-01-15T12:00:00Z".formatted(year));
    }

    @Autowired
    private GameweekRepository gameweeks;

    @Autowired
    private GameweekFixtureRepository fixtures;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private UserRepository users;

    @Autowired
    private CompetitionRepository competitions;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private User player;

    /** A hand-built service: the point is the clock, and the bean's is real. */
    private GameweekService serviceAt(Instant now) {
        return new GameweekService(gameweeks, fixtures, matches, predictions, users, jdbc,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        player = users.save(new User("open-round-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "Player", "FR", null));
    }

    /** A published round over the given window, with one fixture on the card. */
    private long round(Instant now, int weekIndex, Duration fromNow, Duration toNow) {
        GameweekService service = serviceAt(now);
        return tx.execute(status -> {
            Competition competition = competitions.findByCode("OPENRD")
                    .orElseGet(() -> competitions.save(new Competition("OPENRD", "Open Round Cup")));
            String ref = "openround:%d:%s".formatted(weekIndex, UUID.randomUUID());
            Team home = teams.save(new Team("Home " + ref, "HOM", null, ref + ":h"));
            Team away = teams.save(new Team("Away " + ref, "AWY", null, ref + ":a"));
            Match match = matches.save(new Match(competition, home, away,
                    now.plus(fromNow).plus(Duration.ofHours(1)), MatchStatus.SCHEDULED, ref));
            String season = "%d-%02d".formatted(now.atZone(ZoneOffset.UTC).getYear(),
                    (now.atZone(ZoneOffset.UTC).getYear() + 1) % 100);
            var draft = service.createDraft(season, weekIndex, Gameweek.Type.WEEKEND,
                    now.plus(fromNow), now.plus(toNow), true);
            service.setFixtures(draft.id(), List.of(match.getId()));
            // the window comes from the kickoffs once fixtures are set; put it back
            service.rescheduleDraft(draft.id(), now.plus(fromNow), now.plus(toNow), true);
            return service.publish(draft.id()).id();
        });
    }

    private long currentAt(Instant now) {
        GameweekService service = serviceAt(now);
        return tx.execute(status -> service.currentForUser(player.getId()).id());
    }

    @Test
    void theRoundBeingPlayedWinsOverTheOnesAroundIt() {
        Instant now = year(2087);
        long past = round(now, 1, Duration.ofDays(-10), Duration.ofDays(-8));
        long open = round(now, 2, Duration.ofDays(-1), Duration.ofDays(1));
        long ahead = round(now, 3, Duration.ofDays(8), Duration.ofDays(10));

        assertThat(currentAt(now)).isEqualTo(open).isNotIn(past, ahead);
    }

    @Test
    void betweenTwoRoundsItIsTheNextOneThatOpens() {
        Instant now = year(2088);
        long past = round(now, 4, Duration.ofDays(-10), Duration.ofDays(-8));
        long next = round(now, 5, Duration.ofDays(3), Duration.ofDays(5));
        long later = round(now, 6, Duration.ofDays(10), Duration.ofDays(12));

        // publishing two rounds ahead must not skip the nearer one
        assertThat(currentAt(now)).isEqualTo(next).isNotIn(past, later);
    }

    @Test
    void whenTheSeasonIsOverItIsTheLastRoundPlayed() {
        // the very end of the calendar, so that nothing lies ahead of it
        Instant now = year(2099);
        round(now, 7, Duration.ofDays(-20), Duration.ofDays(-18));
        long last = round(now, 8, Duration.ofDays(-10), Duration.ofDays(-8));

        assertThat(currentAt(now)).isEqualTo(last);
    }
}
