package com.predictor.league;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PrivateLeagueFlowIT {

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

    private String founderToken;
    private String joinerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        founderToken = tokenFor("founder@example.com");
        joinerToken = tokenFor("joiner@example.com");
        outsiderToken = tokenFor("outsider@example.com");
    }

    private String tokenFor(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new User(email, passwordEncoder.encode("correct-horse"), email.split("@")[0], "FR", null)));
        return jwtService.issueToken(user);
    }

    private JsonNode createLeague(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leagues")
                        .header("Authorization", "Bearer " + founderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void createJoinAndVisibilityRules() throws Exception {
        JsonNode league = createLeague("Flow League");
        long leagueId = league.get("id").asLong();
        String code = league.get("inviteCode").asText();
        assertThat(code).matches("[A-HJ-KM-NP-Z2-9]{8}");

        // join with lowercase code succeeds
        mockMvc.perform(post("/api/leagues/join")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.admin").value(false));

        // duplicate join → 409, bad code → 404
        mockMvc.perform(post("/api/leagues/join")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/leagues/join")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"WRONGCOD\"}"))
                .andExpect(status().isNotFound());

        // non-member sees 404 on detail and regenerate; member-not-admin gets 403 on regenerate
        mockMvc.perform(get("/api/leagues/" + leagueId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/leagues/%d/regenerate-code".formatted(leagueId))
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/leagues/%d/regenerate-code".formatted(leagueId))
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isForbidden());

        // admin regenerates; the old code stops working
        MvcResult regenerated = mockMvc.perform(post("/api/leagues/%d/regenerate-code".formatted(leagueId))
                        .header("Authorization", "Bearer " + founderToken))
                .andExpect(status().isOk())
                .andReturn();
        String newCode = objectMapper.readTree(regenerated.getResponse().getContentAsString())
                .get("inviteCode").asText();
        assertThat(newCode).isNotEqualTo(code);
        mockMvc.perform(post("/api/leagues/join")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isNotFound());

        // mine shows the league with member count
        mockMvc.perform(get("/api/leagues/mine")
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].memberCount".formatted(leagueId)).value(2));

        // admin can't leave while others remain; member leaves fine; then admin leave deletes
        mockMvc.perform(delete("/api/leagues/%d/members/me".formatted(leagueId))
                        .header("Authorization", "Bearer " + founderToken))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/leagues/%d/members/me".formatted(leagueId))
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/leagues/%d/members/me".formatted(leagueId))
                        .header("Authorization", "Bearer " + founderToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/leagues/" + leagueId)
                        .header("Authorization", "Bearer " + founderToken))
                .andExpect(status().isNotFound());
    }
}
