package com.predictor.scoring;

import com.predictor.TestcontainersConfiguration;
import com.predictor.auth.JwtService;
import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.catalog.MatchStatus;
import com.predictor.fixtures.FixtureSyncService;
import com.predictor.gameweek.Gameweek;
import com.predictor.gameweek.GameweekDtos.GameweekView;
import com.predictor.gameweek.GameweekRepository;
import com.predictor.gameweek.GameweekService;
import com.predictor.prediction.PredictionService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ScoringFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private GameweekRepository gameweeks;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private com.predictor.gameweek.GameweekFixtureRepository fixtures;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private TransactionTemplate tx;

    private User exactUser;
    private User diffUser;
    private User drawUser;
    private long gameweekId;
    private long openFixtureId;
    private long openMatchId;
    private List<Long> matchIds;

    @BeforeEach
    void setUp() {
        syncService.syncAll();
        exactUser = userFor("exact@example.com");
        diffUser = userFor("diff@example.com");
        drawUser = userFor("draw@example.com");

        Instant now = Instant.now();
        matchIds = matches
                .findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(
                        "SA", now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(5)))
                .stream().map(Match::getId).toList();
        assertThat(matchIds).hasSize(6);

        var existing = gameweekService.listAll().stream()
                .filter(gw -> gw.season().equals("9996-97")).findFirst();
        GameweekView gameweek = existing.orElseGet(() -> {
            GameweekView draft = gameweekService.createDraft("9996-97", 1, Gameweek.Type.MIDWEEK,
                    now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(4)));
            gameweekService.setFixtures(draft.id(), matchIds);
            return gameweekService.publish(draft.id());
        });
        gameweekId = gameweek.id();
        gameweek.fixtures().stream()
                .filter(f -> !f.locked() && f.matchStatus().equals("TIMED"))
                .findFirst()
                .ifPresent(f -> openFixtureId = f.fixtureId());
        assertThat(openFixtureId).isPositive();
        openMatchId = tx.execute(status ->
                fixtures.findByIdAndGameweekId(openFixtureId, gameweekId).orElseThrow().getMatch().getId());
    }

    private User userFor(String email) {
        return users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new User(email, passwordEncoder.encode("correct-horse"), email.split("@")[0], "FR", null)));
    }

    private void setResultAndScore(long matchId, int home, int away) {
        tx.executeWithoutResult(status -> {
            Match match = matches.findById(matchId).orElseThrow();
            match.setScore(home, away);
            match.setStatus(MatchStatus.FINISHED);
        });
        scoringService.scoreMatch(matchId);
    }

    private Integer pointsFor(User user) throws Exception {
        String token = jwtService.issueToken(user);
        JsonNode gw = objectMapper.readTree(mockMvc.perform(get("/api/gameweeks/" + gameweekId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode fixture : gw.get("fixtures")) {
            if (fixture.get("fixtureId").asLong() == openFixtureId) {
                JsonNode prediction = fixture.get("prediction");
                if (prediction == null || prediction.isNull()) {
                    return null;
                }
                JsonNode points = prediction.get("points");
                return points == null || points.isNull() ? null : points.asInt();
            }
        }
        return null;
    }

    @Test
    void scoresRescoresAndSettlesGameweekAndRanksGlobally() throws Exception {
        predictionService.upsert(exactUser.getId(), gameweekId, openFixtureId, 2, 1);
        predictionService.upsert(diffUser.getId(), gameweekId, openFixtureId, 3, 2);
        predictionService.upsert(drawUser.getId(), gameweekId, openFixtureId, 2, 2);

        // Result 2-1: exact=3, same GD win=2, draw predicted=0
        setResultAndScore(openMatchId, 2, 1);
        assertThat(pointsFor(exactUser)).isEqualTo(3);
        assertThat(pointsFor(diffUser)).isEqualTo(2);
        assertThat(pointsFor(drawUser)).isZero();

        // Idempotent re-run
        scoringService.scoreMatch(openMatchId);
        assertThat(pointsFor(exactUser)).isEqualTo(3);

        // Correction to 1-1: the draw rule gives the inexact draw exactly 1
        setResultAndScore(openMatchId, 1, 1);
        assertThat(pointsFor(exactUser)).isZero();
        assertThat(pointsFor(diffUser)).isZero();
        assertThat(pointsFor(drawUser)).isEqualTo(1);

        // Settle every match in the gameweek → SCORED
        for (Long matchId : matchIds) {
            setResultAndScore(matchId, 1, 0);
        }
        // restore the tested match's corrected result afterwards so table checks stay deterministic
        setResultAndScore(openMatchId, 1, 1);
        assertThat(gameweeks.findById(gameweekId).orElseThrow().getStatus())
                .isEqualTo(Gameweek.Status.SCORED);

        // Global table reflects totals and gives every user a row
        String token = jwtService.issueToken(drawUser);
        JsonNode table = objectMapper.readTree(mockMvc.perform(
                        get("/api/leagues/global/table?size=100")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(table.get("me").get("userId").asLong()).isEqualTo(drawUser.getId());
        assertThat(table.get("me").get("points").asInt()).isEqualTo(1);
        long previous = Long.MAX_VALUE;
        for (JsonNode entry : table.get("entries")) {
            assertThat(entry.get("points").asLong()).isLessThanOrEqualTo(previous);
            previous = entry.get("points").asLong();
        }
    }
}
