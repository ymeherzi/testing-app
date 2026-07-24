package com.predictor.gameweek;

import com.predictor.TestcontainersConfiguration;
import com.predictor.auth.JwtService;
import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.fixtures.FixtureSyncService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PlayerPredictionsIT {

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
    private GameweekFixtureRepository fixtures;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private TransactionTemplate tx;

    private String viewerToken;
    private User rival;
    private long gameweekId;
    private long lockedFixtureId;
    private long openFixtureId;

    @BeforeEach
    void setUp() {
        syncService.syncAll();
        User viewer = userFor("peek-viewer@example.com");
        rival = userFor("peek-rival@example.com");
        viewerToken = jwtService.issueToken(viewer);

        Instant now = Instant.now();
        List<Long> matchIds = matches
                .findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(
                        "CL", now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(5)))
                .stream().map(Match::getId).toList();
        var existing = gameweekService.listAll().stream()
                .filter(gw -> gw.season().equals("9994-95")).findFirst();
        gameweekId = existing.map(GameweekDtos.GameweekView::id).orElseGet(() -> {
            long id = gameweekService.createDraft("9994-95", 1, Gameweek.Type.WEEKEND,
                    now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(4))).id();
            gameweekService.setFixtures(id, matchIds);
            gameweekService.publish(id);
            return id;
        });

        tx.executeWithoutResult(s -> {
            for (GameweekFixture fixture : fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId)) {
                boolean locked = gameweekService.isLocked(fixture.getMatch(), Instant.now());
                if (locked && lockedFixtureId == 0) {
                    lockedFixtureId = fixture.getId();
                } else if (!locked && openFixtureId == 0) {
                    openFixtureId = fixture.getId();
                }
            }
        });
        assertThat(lockedFixtureId).isPositive();
        assertThat(openFixtureId).isPositive();
    }

    private User userFor(String email) {
        return users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new User(email, passwordEncoder.encode("correct-horse"), email.split("@")[0], "TN", null)));
    }

    @Test
    void rivalPicksAreHiddenBeforeKickoffAndRevealedAfter() throws Exception {
        // the rival predicts an open fixture; nobody may see it yet
        predictionService.upsert(rival.getId(), gameweekId, openFixtureId, 3, 0);

        JsonNode view = objectMapper.readTree(mockMvc.perform(
                        get("/api/gameweeks/%d/players/%d".formatted(gameweekId, rival.getId()))
                                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("peek-rival"))
                .andReturn().getResponse().getContentAsString());

        assertThat(view.get("hiddenCount").asInt()).isEqualTo(1);
        for (JsonNode fixture : view.get("fixtures")) {
            if (fixture.get("fixtureId").asLong() == openFixtureId) {
                assertThat(fixture.get("locked").asBoolean()).isFalse();
                assertThat(fixture.get("prediction").isNull()).isTrue(); // anti-copying
            }
        }

        // a prediction on an already-locked fixture is visible (seeded via repository,
        // since the API rightly refuses to accept predictions after kickoff)
        tx.executeWithoutResult(s -> {
            GameweekFixture fixture = fixtures.findById(lockedFixtureId).orElseThrow();
            s.flush();
            predictions().save(new com.predictor.prediction.Prediction(
                    rival.getId(), fixture, 2, 1, Instant.now()));
        });

        JsonNode after = objectMapper.readTree(mockMvc.perform(
                        get("/api/gameweeks/%d/players/%d".formatted(gameweekId, rival.getId()))
                                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(after.get("revealedCount").asInt()).isEqualTo(1);
        boolean sawRevealed = false;
        for (JsonNode fixture : after.get("fixtures")) {
            if (fixture.get("fixtureId").asLong() == lockedFixtureId) {
                assertThat(fixture.get("prediction").get("homeGoals").asInt()).isEqualTo(2);
                sawRevealed = true;
            }
        }
        assertThat(sawRevealed).isTrue();
    }

    @Test
    void unauthenticatedAccessIsRejectedAndUnknownPlayerIs404() throws Exception {
        mockMvc.perform(get("/api/gameweeks/%d/players/%d".formatted(gameweekId, rival.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gameweeks/%d/players/999999".formatted(gameweekId))
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Autowired
    private com.predictor.prediction.PredictionRepository predictionRepository;

    private com.predictor.prediction.PredictionRepository predictions() {
        return predictionRepository;
    }
}
