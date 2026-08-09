package com.prono10.league;

import com.prono10.TestcontainersConfiguration;
import com.prono10.auth.JwtService;
import com.prono10.catalog.Team;
import com.prono10.catalog.TeamRepository;
import com.prono10.user.User;
import com.prono10.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
class PublicLeagueTableIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Team club;
    private String scopedFrToken;
    private String noCountryToken;

    @BeforeEach
    void setUp() {
        club = teams.findFirstByNameIgnoreCase("Scoped Test Club").orElseGet(() ->
                teams.save(new Team("Scoped Test Club", "STC", null, null)));
        scopedFrToken = tokenFor("scoped-fr@example.com", "FR", club.getId());
        tokenFor("scoped-fr2@example.com", "FR", null);
        tokenFor("scoped-de@example.com", "DE", club.getId());
        noCountryToken = tokenFor("scoped-none@example.com", null, null);
    }

    private String tokenFor(String email, String country, Long clubId) {
        User user = users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new User(email, passwordEncoder.encode("correct-horse"), email.split("@")[0],
                        country, clubId)));
        return jwtService.issueToken(user);
    }

    @Test
    void countryTableContainsOnlyCallersCountry() throws Exception {
        JsonNode scoped = objectMapper.readTree(mockMvc.perform(get("/api/leagues/country/table")
                        .header("Authorization", "Bearer " + scopedFrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.country").value("FR"))
                .andReturn().getResponse().getContentAsString());
        long previousRank = 0;
        for (JsonNode entry : scoped.get("table").get("entries")) {
            assertThat(entry.get("country").asText()).isEqualTo("FR");
            assertThat(entry.get("rank").asLong()).isGreaterThanOrEqualTo(previousRank);
            previousRank = entry.get("rank").asLong();
        }
        assertThat(scoped.get("table").get("entries").size()).isGreaterThanOrEqualTo(2);
        assertThat(scoped.get("table").get("me").get("userId").asText()).isNotBlank();
    }

    @Test
    void clubTableContainsOnlyCallersClubAcrossCountries() throws Exception {
        JsonNode scoped = objectMapper.readTree(mockMvc.perform(get("/api/leagues/club/table")
                        .header("Authorization", "Bearer " + scopedFrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.clubName").value("Scoped Test Club"))
                .andReturn().getResponse().getContentAsString());
        // both the FR and DE fans of the club, nobody else's club
        assertThat(scoped.get("table").get("entries").size()).isGreaterThanOrEqualTo(2);
        assertThat(scoped.get("table").get("totalPlayers").asLong()).isEqualTo(2);
    }

    @Test
    void missingAttributesYieldEmptyState() throws Exception {
        mockMvc.perform(get("/api/leagues/country/table")
                        .header("Authorization", "Bearer " + noCountryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(get("/api/leagues/club/table")
                        .header("Authorization", "Bearer " + noCountryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
