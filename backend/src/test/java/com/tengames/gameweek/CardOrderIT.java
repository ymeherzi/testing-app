package com.tengames.gameweek;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.catalog.Team;
import com.tengames.catalog.TeamRepository;
import com.tengames.gameweek.GameweekDtos.FixtureView;
import com.tengames.gameweek.GameweekDtos.GameweekView;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Saturday at three o'clock holds several matches, and they used to come
 * back in whatever order the database chose — three La Liga games and a
 * Premier League one interleaved. Fixtures starting at the same minute belong
 * together, biggest competition first.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CardOrderIT {

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private CompetitionRepository competitions;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private User player;

    @BeforeEach
    void setUp() {
        player = users.save(new User("order-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "Order", "TN", null));
    }

    private Competition competition(String code) {
        return competitions.findByCode(code).orElseGet(() -> competitions.save(new Competition(code, code)));
    }

    private long match(String competitionCode, String label, Instant kickoff) {
        String ref = "order:%s:%s".formatted(competitionCode, label);
        Team home = teams.save(new Team("Home " + ref, "HOM", null, ref + ":h"));
        Team away = teams.save(new Team("Away " + ref, "AWY", null, ref + ":a"));
        return matches.save(new Match(competition(competitionCode), home, away, kickoff,
                MatchStatus.SCHEDULED, ref)).getId();
    }

    /**
     * The window sits in the past so this round cannot become the "current or
     * next" one for the suites sharing this database; the kickoffs are what
     * the ordering looks at.
     */
    private long publishedRound(String season, List<Long> matchIds) {
        Instant now = clock.instant();
        GameweekView draft = gameweekService.createDraft(season, 1, Gameweek.Type.WEEKEND,
                now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(59)), true);
        gameweekService.setFixtures(draft.id(), matchIds);
        gameweekService.rescheduleDraft(draft.id(), now.minus(Duration.ofDays(60)),
                now.minus(Duration.ofDays(59)), true);
        return gameweekService.publish(draft.id()).id();
    }

    private List<String> competitionOrder(long gameweekId) {
        return gameweekService.byIdForUser(gameweekId, player.getId()).fixtures().stream()
                .map(FixtureView::competitionCode)
                .toList();
    }

    @Test
    void fixturesKickingOffTogetherAreGroupedByCompetition() {
        Instant threeOClock = Instant.parse("2027-08-21T14:00:00Z");
        // deliberately interleaved on the way in, and saved in that order
        List<Long> pool = new ArrayList<>(List.of(
                match("PD", "a", threeOClock),
                match("PL", "a", threeOClock),
                match("PD", "b", threeOClock),
                match("PL", "b", threeOClock),
                match("PD", "c", threeOClock)));
        long round = publishedRound("9950-01", pool);

        // neither competition is split apart, which is the whole point. The
        // Premier League and La Liga carry the same weight, so which of the
        // two leads is settled by the code — stable, and nobody's business at
        // three o'clock on a Saturday
        assertThat(competitionOrder(round)).containsExactly("PD", "PD", "PD", "PL", "PL");
        // and the order is the same on the next load — nothing here is
        // decided by the database's mood
        assertThat(competitionOrder(round)).isEqualTo(competitionOrder(round));
    }

    @Test
    void kickoffStillDecidesEverythingElse() {
        Instant threeOClock = Instant.parse("2027-08-22T14:00:00Z");
        List<Long> pool = new ArrayList<>(List.of(
                match("PD", "late", threeOClock.plus(Duration.ofMinutes(15))),
                match("PL", "early", threeOClock),
                match("PD", "same", threeOClock)));
        long round = publishedRound("9950-02", pool);

        // the easy mistake is gathering a whole competition together: the
        // 15:15 La Liga game must stay after the 15:00 pair, not jump up to
        // join the other La Liga match
        assertThat(competitionOrder(round)).containsExactly("PD", "PL", "PD");
        assertThat(gameweekService.byIdForUser(round, player.getId()).fixtures())
                .extracting(FixtureView::kickoffUtc)
                .containsExactly(threeOClock, threeOClock, threeOClock.plus(Duration.ofMinutes(15)));
    }

    @Test
    void aCupFinalLeadsTheLeaguesItSharesAKickoffWith() {
        Instant kickoff = Instant.parse("2027-08-23T14:00:00Z");
        long round = publishedRound("9950-03", new ArrayList<>(List.of(
                match("PL", "x", kickoff),
                match("CSHIELD", "final", kickoff),
                match("FL1", "y", kickoff))));

        assertThat(competitionOrder(round)).containsExactly("CSHIELD", "PL", "FL1");
    }
}
