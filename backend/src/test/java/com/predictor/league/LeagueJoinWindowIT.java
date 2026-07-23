package com.predictor.league;

import com.predictor.TestcontainersConfiguration;
import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.catalog.MatchStatus;
import com.predictor.fixtures.FixtureSyncService;
import com.predictor.gameweek.Gameweek;
import com.predictor.gameweek.GameweekFixtureRepository;
import com.predictor.gameweek.GameweekService;
import com.predictor.prediction.PredictionService;
import com.predictor.scoring.ScoringService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money test for design §5.3: private league points count from each
 * member's join gameweek, while global totals keep everything.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LeagueJoinWindowIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private GameweekFixtureRepository fixtures;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueTableService tableService;

    @Autowired
    private LeagueMemberRepository members;

    @Autowired
    private TransactionTemplate tx;

    private User userFor(String email) {
        return users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new User(email, passwordEncoder.encode("correct-horse"), email.split("@")[0], "FR", null)));
    }

    private long publishGameweek(String season, int index, Instant windowStart, Instant windowEnd,
                                 String competition) {
        Instant now = Instant.now();
        List<Long> matchIds = matches
                .findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(
                        competition, now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(5)))
                .stream().map(Match::getId).limit(4).toList();
        long id = gameweekService.createDraft(season, index, Gameweek.Type.WEEKEND, windowStart, windowEnd).id();
        gameweekService.setFixtures(id, matchIds);
        gameweekService.publish(id);
        return id;
    }

    private long openFixtureOf(long gameweekId) {
        return tx.execute(s -> fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId).stream()
                .filter(f -> f.getMatch().getStatus() == MatchStatus.TIMED)
                .findFirst().orElseThrow().getId());
    }

    private void scoreFixture(long fixtureId, int home, int away) {
        long matchId = tx.execute(s -> fixtures.findById(fixtureId).orElseThrow().getMatch().getId());
        tx.executeWithoutResult(s -> {
            Match match = matches.findById(matchId).orElseThrow();
            match.setScore(home, away);
            match.setStatus(MatchStatus.FINISHED);
        });
        scoringService.scoreMatch(matchId);
    }

    @Test
    void leaguePointsCountFromJoinGameweekWhileGlobalKeepsAll() {
        syncService.syncAll();
        User alice = userFor("window-alice@example.com");
        User bob = userFor("window-bob@example.com");

        Instant now = Instant.now();
        long gw1 = publishGameweek("9995-96", 1, now.minus(Duration.ofDays(1)), now.plus(Duration.ofHours(4)), "BL1");
        long gw2 = publishGameweek("9995-96", 2, now.plus(Duration.ofHours(5)), now.plus(Duration.ofDays(4)), "FL1");

        // Alice creates the league while GW1 is the current-or-next gameweek
        var league = leagueService.create(alice.getId(), "Window League");
        assertThat(members.findByLeagueIdAndUserId(league.id(), alice.getId()).orElseThrow()
                .getJoinGameweekId()).isEqualTo(gw1);

        // both users predict identical scorelines in both gameweeks
        long fixtureGw1 = openFixtureOf(gw1);
        long fixtureGw2 = openFixtureOf(gw2);
        predictionService.upsert(alice.getId(), gw1, fixtureGw1, 2, 1);
        predictionService.upsert(bob.getId(), gw1, fixtureGw1, 2, 1);
        predictionService.upsert(alice.getId(), gw2, fixtureGw2, 1, 0);
        predictionService.upsert(bob.getId(), gw2, fixtureGw2, 1, 0);

        scoreFixture(fixtureGw1, 2, 1); // both exact → 3 points globally each

        // Bob joins "later": emulate a join during GW2 by writing his
        // membership with join_gameweek_id = GW2 (what resolveJoinGameweekId
        // would produce once GW1's window has ended)
        League leagueEntity = tx.execute(s ->
                members.findByLeagueIdAndUserId(league.id(), alice.getId()).orElseThrow().getLeague());
        members.save(new LeagueMember(leagueEntity, bob.getId(), gw2, Instant.now()));

        scoreFixture(fixtureGw2, 1, 0); // both exact again → +3 globally each

        var table = tableService.leagueMembersTable(league.id());
        var aliceRow = table.stream().filter(e -> e.userId() == alice.getId()).findFirst().orElseThrow();
        var bobRow = table.stream().filter(e -> e.userId() == bob.getId()).findFirst().orElseThrow();
        assertThat(aliceRow.points()).isEqualTo(6); // GW1 + GW2
        assertThat(bobRow.points()).isEqualTo(3);   // GW2 only — pre-join points excluded
        assertThat(aliceRow.rank()).isEqualTo(1);
        assertThat(bobRow.rank()).isEqualTo(2);
        assertThat(aliceRow.admin()).isTrue();

        // globally, Bob keeps the full total
        var global = tableService.globalTable(bob.getId(), 0, 100);
        assertThat(global.me().points()).isGreaterThanOrEqualTo(6);
    }
}
