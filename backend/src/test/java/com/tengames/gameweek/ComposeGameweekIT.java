package com.tengames.gameweek;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.JwtService;
import com.tengames.fixtures.FixtureSyncService;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Composing a round is the admin's weekly chore, and the one screen where a
 * bad shortcut is expensive: a half-filled or wrongly-flagged gameweek is
 * what every player then plays.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ComposeGameweekIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String playerToken;

    @BeforeEach
    void setUp() {
        syncService.syncAll();
        User admin = users.save(admin("compose-admin-%s@example.com".formatted(UUID.randomUUID())));
        adminToken = jwtService.issueToken(admin);
        playerToken = jwtService.issueToken(
                users.save(new User("compose-player-%s@example.com".formatted(UUID.randomUUID()),
                        passwordEncoder.encode("correct-horse"), "Player", "TN", null)));
    }

    private User admin(String email) {
        User user = new User(email, passwordEncoder.encode("correct-horse"), "Admin", "TN", null);
        user.setAdmin(true);
        return user;
    }

    private String composeBody(String season, boolean counts, int size) {
        Instant from = Instant.now().minus(Duration.ofDays(1));
        Instant to = Instant.now().plus(Duration.ofDays(6));
        return """
                {"season":"%s","weekIndex":1,"windowStart":"%s","windowEnd":"%s",
                 "countsTowardsTable":%s,"size":%d}""".formatted(season, from, to, counts, size);
    }

    private JsonNode compose(String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/gameweeks/compose")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void oneCallCreatesTheRoundAndFillsIt() throws Exception {
        JsonNode gameweek = compose(composeBody("9980-81", true, 10));

        assertThat(gameweek.get("fixtures")).hasSize(10);
        assertThat(gameweek.get("status").asText()).isEqualTo("DRAFT");
        assertThat(gameweek.get("countsTowardsTable").asBoolean()).isTrue();
        // every fixture must be distinct — a card with the same game twice
        // would be nonsense to play
        assertThat(gameweek.get("fixtures").valueStream()
                .map(f -> f.get("fixtureId").asLong()).distinct().count()).isEqualTo(10);
        // and readable: the whole point of the screen is choosing by name
        assertThat(gameweek.get("fixtures").get(0).get("homeTeam").get("name").asText()).isNotBlank();
    }

    @Test
    void aWarmUpRoundIsComposedTheSameWayButFlagged() throws Exception {
        JsonNode gameweek = compose(composeBody("9980-82", false, 10));

        assertThat(gameweek.get("countsTowardsTable").asBoolean()).isFalse();
        assertThat(gameweek.get("fixtures")).hasSize(10);
    }

    @Test
    void anEmptyWindowIsRefusedWithSomethingActionable() throws Exception {
        String body = """
                {"season":"9980-83","weekIndex":1,"windowStart":"%s","windowEnd":"%s"}"""
                .formatted(Instant.now().plus(Duration.ofDays(900)),
                        Instant.now().plus(Duration.ofDays(901)));

        mockMvc.perform(post("/api/admin/gameweeks/compose")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void composingIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/gameweeks/compose")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(composeBody("9980-84", true, 10)))
                .andExpect(status().isForbidden());
    }
}
