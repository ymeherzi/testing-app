package com.tengames.gameweek;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.JwtService;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.fixtures.FixtureSyncService;
import com.tengames.gameweek.GameweekDtos.GameweekView;
import com.tengames.league.LeagueTableService;
import com.tengames.prediction.PredictionService;
import com.tengames.scoring.ScoringService;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A warm-up round — the "journée blanche" before the season opens — is played
 * and scored like any other, and must leave the standings untouched. Getting
 * this half-right is the dangerous outcome: points that show on the gameweek
 * but silently count in the table would only be noticed once the season is
 * already unfair.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PreviewGameweekIT {

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private GameweekFixtureRepository fixtures;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private LeagueTableService tables;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate tx;

    private User player;

    @BeforeEach
    void setUp() {
        syncService.syncAll();
        player = users.save(new User("preview-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "Preview", "TN", null));
    }

    /**
     * Builds a gameweek over the given matches.
     *
     * <p>The window sits well in the past on purpose: a window straddling now
     * would make these rounds the "current or next" gameweek for every other
     * test sharing this database, which silently changes the join window a
     * league records. Locking is decided by each match's own kickoff, so the
     * fixtures here are still open despite the old window.
     */
    private long publishRound(String season, boolean counts, List<Long> matchIds) {
        Instant now = Instant.now();
        GameweekView draft = gameweekService.createDraft(season, 1, Gameweek.Type.WEEKEND,
                now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(59)), counts);
        gameweekService.setFixtures(draft.id(), matchIds);
        // setFixtures derives the window from the fixtures, which would drag
        // these rounds back over "now"; push them out again before publishing.
        gameweekService.rescheduleDraft(draft.id(), now.minus(Duration.ofDays(60)),
                now.minus(Duration.ofDays(59)), counts);
        GameweekView published = gameweekService.publish(draft.id());
        assertThat(published.countsTowardsTable()).isEqualTo(counts);
        return published.id();
    }

    private long openFixtureOf(long gameweekId) {
        return gameweekService.byIdForUser(gameweekId, player.getId()).fixtures().stream()
                .filter(f -> !f.locked())
                .findFirst().orElseThrow().fixtureId();
    }

    private void finish(long fixtureId, long gameweekId, int home, int away) {
        long matchId = tx.execute(status ->
                fixtures.findByIdAndGameweekId(fixtureId, gameweekId).orElseThrow().getMatch().getId());
        tx.executeWithoutResult(status -> {
            Match match = matches.findById(matchId).orElseThrow();
            match.setScore(home, away);
            match.setStatus(MatchStatus.FINISHED);
        });
        scoringService.scoreMatch(matchId);
    }

    private long globalPoints() {
        return tables.globalTable(player.getId(), 0, 200).entries().stream()
                .filter(e -> e.userId().equals(player.getPublicId()))
                .findFirst().orElseThrow().points();
    }

    @Test
    void aPreviewRoundIsScoredButNeverRanked() {
        List<Long> pool = matches.findAll().stream().map(Match::getId).limit(4).toList();
        long preview = publishRound("9990-91", false, pool);
        long fixtureId = openFixtureOf(preview);

        predictionService.upsert(player.getId(), preview, fixtureId, 2, 1);
        finish(fixtureId, preview, 2, 1);

        // the player sees the exact-scoreline points on the round itself…
        long earned = gameweekService.history(player.getId()).stream()
                .filter(gw -> gw.id() == preview)
                .findFirst().orElseThrow().myPoints();
        assertThat(earned).isEqualTo(3);

        // …and the season table ignores them entirely
        assertThat(globalPoints()).isZero();
    }

    @Test
    void anOrdinaryRoundStillCounts() {
        List<Long> pool = matches.findAll().stream().map(Match::getId).skip(4).limit(4).toList();
        long round = publishRound("9990-92", true, pool);
        long fixtureId = openFixtureOf(round);

        predictionService.upsert(player.getId(), round, fixtureId, 2, 1);
        finish(fixtureId, round, 2, 1);

        assertThat(globalPoints()).isEqualTo(3);
    }

    @Test
    void aPlayerWhoOnlyPlayedThePreviewStillAppearsInTheTable() {
        List<Long> pool = matches.findAll().stream().map(Match::getId).skip(8).limit(4).toList();
        long preview = publishRound("9990-93", false, pool);
        long fixtureId = openFixtureOf(preview);
        predictionService.upsert(player.getId(), preview, fixtureId, 1, 1);
        finish(fixtureId, preview, 1, 1);

        // excluding preview points must not exclude the player: an inner join
        // here would drop them from the rankings altogether
        assertThat(tables.globalTable(player.getId(), 0, 200).entries())
                .anyMatch(e -> e.userId().equals(player.getPublicId()));
        assertThat(globalPoints()).isZero();
    }
}
