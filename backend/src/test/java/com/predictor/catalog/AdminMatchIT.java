package com.predictor.catalog;

import com.predictor.TestcontainersConfiguration;
import com.predictor.auth.JwtService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminMatchIT {

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

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = tokenFor("match-admin@example.com", true);
        userToken = tokenFor("match-user@example.com", false);
    }

    private String tokenFor(String email, boolean admin) {
        User user = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User created = new User(email, passwordEncoder.encode("correct-horse"), "Match Tester", null, null);
            created.setAdmin(admin);
            return users.save(created);
        });
        return jwtService.issueToken(user);
    }

    @Test
    void adminCreatesManualMatchInNewCompetitionAndEntersResult() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/matches")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"competitionCode": "friendly", "competitionName": "Club Friendlies",
                                 "homeTeam": "Ajax", "awayTeam": "Benfica",
                                 "kickoffUtc": "2026-07-25T18:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionCode").value("FRIENDLY"))
                .andExpect(jsonPath("$.homeTeam.name").value("Ajax"))
                .andExpect(jsonPath("$.status").value("TIMED"))
                .andReturn();
        long matchId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // team resolution is by name: reusing Ajax must not duplicate the team
        mockMvc.perform(post("/api/admin/matches")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"competitionCode": "FRIENDLY", "homeTeam": "Porto", "awayTeam": "ajax",
                                 "kickoffUtc": "2026-07-26T18:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awayTeam.name").value("Ajax"));

        mockMvc.perform(post("/api/admin/matches/%d/result".formatted(matchId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\": 2, \"awayScore\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(2));
    }

    @Test
    void selfPlayAndNonAdminsAreRejected() throws Exception {
        mockMvc.perform(post("/api/admin/matches")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"competitionCode": "FRIENDLY", "homeTeam": "Ajax", "awayTeam": "AJAX",
                                 "kickoffUtc": "2026-07-25T18:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/matches")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"competitionCode": "FRIENDLY", "homeTeam": "A", "awayTeam": "B",
                                 "kickoffUtc": "2026-07-25T18:00:00Z"}
                                """))
                .andExpect(status().isForbidden());
    }
}
