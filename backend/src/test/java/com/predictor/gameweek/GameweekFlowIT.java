package com.predictor.gameweek;

import com.predictor.TestcontainersConfiguration;
import com.predictor.auth.JwtService;
import com.predictor.fixtures.FixtureSyncService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
import java.time.Instant;
import java.util.stream.Collectors;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GameweekFlowIT {

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

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        syncService.syncAll();
        adminToken = tokenFor("gw-admin@example.com", true);
        userToken = tokenFor("gw-user@example.com", false);
    }

    private String tokenFor(String email, boolean admin) {
        User user = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User created = new User(email, passwordEncoder.encode("correct-horse"), "GW Tester", "FR", null);
            created.setAdmin(admin);
            return users.save(created);
        });
        return jwtService.issueToken(user);
    }

    @Test
    void adminCuratesAndPublishesGameweekThenUserSeesIt() throws Exception {
        // Browse the synced pool (seeded matches)
        MvcResult pool = mockMvc.perform(get("/api/admin/matches")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode poolJson = objectMapper.readTree(pool.getResponse().getContentAsString());
        assertThat(poolJson.size()).isGreaterThanOrEqualTo(6);

        // Mix of started (locked) and upcoming (open) matches: 3 earliest + 3 latest
        var allIds = java.util.stream.StreamSupport.stream(poolJson.spliterator(), false)
                .map(node -> node.get("id").asText())
                .toList();
        String matchIds = java.util.stream.Stream
                .concat(allIds.stream().limit(3), allIds.stream().skip(allIds.size() - 3))
                .collect(Collectors.joining(","));

        // Create a draft gameweek
        Instant now = Instant.now();
        MvcResult created = mockMvc.perform(post("/api/admin/gameweeks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"season": "9998-99", "weekIndex": 1, "type": "WEEKEND",
                                 "windowStart": "%s", "windowEnd": "%s"}
                                """.formatted(now.minusSeconds(86400), now.plusSeconds(4 * 86400))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long gameweekId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Publishing without fixtures is rejected
        mockMvc.perform(post("/api/admin/gameweeks/%d/publish".formatted(gameweekId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // Set fixtures and publish
        mockMvc.perform(put("/api/admin/gameweeks/%d/fixtures".formatted(gameweekId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\": [%s]}".formatted(matchIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures.length()").value(6));

        mockMvc.perform(post("/api/admin/gameweeks/%d/publish".formatted(gameweekId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Editing fixtures after publish is rejected
        mockMvc.perform(put("/api/admin/gameweeks/%d/fixtures".formatted(gameweekId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\": [%s]}".formatted(matchIds)))
                .andExpect(status().isConflict());

        // A regular user sees the published gameweek with lock flags
        MvcResult current = mockMvc.perform(get("/api/gameweeks/current")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures.length()").value(6))
                .andReturn();
        JsonNode gw = objectMapper.readTree(current.getResponse().getContentAsString());
        boolean sawLocked = false;
        boolean sawOpen = false;
        for (JsonNode fixture : gw.get("fixtures")) {
            if (fixture.get("locked").asBoolean()) {
                sawLocked = true;
            } else {
                sawOpen = true;
            }
        }
        // Seed data always contains started matches (locked) and future ones (open)
        assertThat(sawLocked).isTrue();
        assertThat(sawOpen).isTrue();
    }
}
