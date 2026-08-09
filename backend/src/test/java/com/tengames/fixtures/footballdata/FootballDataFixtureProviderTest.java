package com.tengames.fixtures.footballdata;

import com.tengames.catalog.MatchStatus;
import com.tengames.fixtures.ProviderMatch;
import com.tengames.fixtures.ProviderTeam;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FootballDataFixtureProviderTest {

    private static final String BASE = "https://api.football-data.org/v4";

    private MockRestServiceServer server;
    private FootballDataFixtureProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new FootballDataFixtureProvider(builder,
                new FootballDataProperties(BASE, "test-key", 100), Clock.systemUTC());
    }

    @Test
    void requiresApiKey() {
        assertThatThrownBy(() -> new FootballDataFixtureProvider(RestClient.builder(),
                new FootballDataProperties(BASE, " ", 100), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FOOTBALL_DATA_API_KEY");
    }

    @Test
    void parsesTeams() {
        server.expect(requestTo(BASE + "/competitions/PL/teams"))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess("""
                        {"teams": [
                          {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS",
                           "crest": "https://crests.football-data.org/57.png", "founded": 1886},
                          {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "tla": "MCI",
                           "crest": "https://crests.football-data.org/65.png"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderTeam> teams = provider.fetchTeams("PL");

        assertThat(teams).hasSize(2);
        assertThat(teams.getFirst().providerRef()).isEqualTo("fd:team:57");
        assertThat(teams.getFirst().name()).isEqualTo("Arsenal FC");
        assertThat(teams.getFirst().shortName()).isEqualTo("Arsenal");
        assertThat(teams.getFirst().crestUrl()).isEqualTo("https://crests.football-data.org/57.png");
    }

    @Test
    void parsesMatchesWithResultsAndStatuses() {
        server.expect(requestTo(BASE + "/competitions/PL/matches?dateFrom=2026-08-01&dateTo=2026-08-31"))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess("""
                        {"matches": [
                          {"id": 1001, "utcDate": "2026-08-15T14:00:00Z", "status": "FINISHED",
                           "homeTeam": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "crest": "c1"},
                           "awayTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "crest": "c2"},
                           "score": {"winner": "HOME_TEAM", "fullTime": {"home": 2, "away": 1}}},
                          {"id": 1002, "utcDate": "2026-08-22T16:30:00Z", "status": "TIMED",
                           "homeTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "crest": "c2"},
                           "awayTeam": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "crest": "c1"},
                           "score": {"winner": null, "fullTime": {"home": null, "away": null}}},
                          {"id": 1003, "utcDate": "2026-08-23T16:30:00Z", "status": "SOME_NEW_STATUS",
                           "homeTeam": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "crest": "c1"},
                           "awayTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "crest": "c2"},
                           "score": null}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderMatch> matches = provider.fetchMatches("PL",
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));

        assertThat(matches).hasSize(3);

        ProviderMatch finished = matches.getFirst();
        assertThat(finished.providerRef()).isEqualTo("fd:match:1001");
        assertThat(finished.kickoffUtc()).isEqualTo(Instant.parse("2026-08-15T14:00:00Z"));
        assertThat(finished.status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(finished.homeScore()).isEqualTo(2);
        assertThat(finished.awayScore()).isEqualTo(1);
        assertThat(finished.homeTeam().providerRef()).isEqualTo("fd:team:57");

        ProviderMatch upcoming = matches.get(1);
        assertThat(upcoming.status()).isEqualTo(MatchStatus.TIMED);
        assertThat(upcoming.homeScore()).isNull();

        // Unknown statuses degrade to SCHEDULED instead of failing the sync
        assertThat(matches.get(2).status()).isEqualTo(MatchStatus.SCHEDULED);
    }
}
