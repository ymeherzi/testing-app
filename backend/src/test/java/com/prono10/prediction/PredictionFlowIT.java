package com.prono10.prediction;

import com.prono10.TestcontainersConfiguration;
import com.prono10.auth.JwtService;
import com.prono10.fixtures.FixtureSyncService;
import com.prono10.gameweek.Gameweek;
import com.prono10.gameweek.GameweekService;
import com.prono10.user.User;
import com.prono10.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PredictionFlowIT {

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
    private com.prono10.catalog.MatchRepository matches;

    private String userToken;
    private long gameweekId;
    private long openFixtureId;
    private long lockedFixtureId;

    @BeforeEach
    void setUp() throws Exception {
        syncService.syncAll();
        User user = users.findByEmailIgnoreCase("player@example.com").orElseGet(() ->
                users.save(new User("player@example.com", passwordEncoder.encode("correct-horse"),
                        "Player", "TN", null)));
        userToken = jwtService.issueToken(user);

        Instant now = Instant.now();
        List<Long> matchIds = matches
                .findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(
                        "PD", now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(5)))
                .stream().map(com.prono10.catalog.Match::getId).toList();
        assertThat(matchIds).hasSize(6);

        var existing = gameweekService.listAll().stream()
                .filter(gw -> gw.season().equals("9997-98"))
                .findFirst();
        if (existing.isPresent()) {
            gameweekId = existing.get().id();
        } else {
            gameweekId = gameweekService.createDraft("9997-98", 1, Gameweek.Type.WEEKEND,
                    now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(4))).id();
            gameweekService.setFixtures(gameweekId, matchIds);
            gameweekService.publish(gameweekId);
        }

        JsonNode gw = objectMapper.readTree(mockMvc.perform(get("/api/gameweeks/" + gameweekId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode fixture : gw.get("fixtures")) {
            if (fixture.get("locked").asBoolean()) {
                lockedFixtureId = fixture.get("fixtureId").asLong();
            } else if (fixture.get("matchStatus").asText().equals("TIMED")) {
                openFixtureId = fixture.get("fixtureId").asLong();
            }
        }
        assertThat(openFixtureId).isPositive();
        assertThat(lockedFixtureId).isPositive();
    }

    private String predictionUrl(long fixtureId) {
        return "/api/gameweeks/%d/fixtures/%d/prediction".formatted(gameweekId, fixtureId);
    }

    @Test
    void predictionCanBeCreatedAndUpdatedBeforeKickoff() throws Exception {
        mockMvc.perform(put(predictionUrl(openFixtureId))
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\": 2, \"awayGoals\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoals").value(2))
                .andExpect(jsonPath("$.awayGoals").value(1))
                .andExpect(jsonPath("$.points").isEmpty());

        mockMvc.perform(put(predictionUrl(openFixtureId))
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\": 0, \"awayGoals\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoals").value(0));

        // The saved prediction is embedded in the gameweek view
        mockMvc.perform(get("/api/gameweeks/" + gameweekId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures[?(@.fixtureId == %d)].prediction.homeGoals".formatted(openFixtureId))
                        .value(0));
    }

    @Test
    void lockedMatchRejectsPredictions() throws Exception {
        mockMvc.perform(put(predictionUrl(lockedFixtureId))
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\": 1, \"awayGoals\": 0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidScorelineIsRejected(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(put(predictionUrl(openFixtureId))
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\": -1, \"awayGoals\": 25}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownFixtureIs404(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(put(predictionUrl(999999))
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\": 1, \"awayGoals\": 1}"))
                .andExpect(status().isNotFound());
    }
}
