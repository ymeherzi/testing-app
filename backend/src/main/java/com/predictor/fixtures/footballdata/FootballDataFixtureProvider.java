package com.predictor.fixtures.footballdata;

import com.predictor.catalog.MatchStatus;
import com.predictor.fixtures.FixtureProvider;
import com.predictor.fixtures.ProviderMatch;
import com.predictor.fixtures.ProviderTeam;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * football-data.org v4 adapter. All calls flow through a shared rate
 * limiter to respect the free-tier quota (10 req/min).
 */
@Component
@ConditionalOnProperty(name = "app.fixtures.provider", havingValue = "footballdata")
public class FootballDataFixtureProvider implements FixtureProvider {

    private static final Logger log = LoggerFactory.getLogger(FootballDataFixtureProvider.class);

    private final RestClient restClient;
    private final ApiRateLimiter rateLimiter;

    public FootballDataFixtureProvider(RestClient.Builder restClientBuilder,
                                       FootballDataProperties properties, Clock clock) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "FOOTBALL_DATA_API_KEY must be set when app.fixtures.provider=footballdata");
        }
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Auth-Token", properties.apiKey())
                .build();
        this.rateLimiter = new ApiRateLimiter(properties.requestsPerMinute(), clock);
    }

    @Override
    public List<ProviderTeam> fetchTeams(String competitionCode) {
        rateLimiter.acquire();
        TeamsResponse response = restClient.get()
                .uri("/competitions/{code}/teams", competitionCode)
                .retrieve()
                .body(TeamsResponse.class);
        return response == null || response.teams() == null ? List.of()
                : response.teams().stream().map(TeamJson::toProviderTeam).toList();
    }

    @Override
    public List<ProviderMatch> fetchMatches(String competitionCode, LocalDate from, LocalDate to) {
        rateLimiter.acquire();
        MatchesResponse response = restClient.get()
                .uri("/competitions/{code}/matches?dateFrom={from}&dateTo={to}", competitionCode, from, to)
                .retrieve()
                .body(MatchesResponse.class);
        return response == null || response.matches() == null ? List.of()
                : response.matches().stream().map(match -> match.toProviderMatch(competitionCode)).toList();
    }

    // --- wire format ---

    record TeamsResponse(List<TeamJson> teams) {
    }

    record TeamJson(Long id, String name, String shortName, String tla, String crest) {

        ProviderTeam toProviderTeam() {
            String display = shortName != null ? shortName : name;
            return new ProviderTeam("fd:team:" + id, name, display, crest);
        }
    }

    record MatchesResponse(List<MatchJson> matches) {
    }

    record MatchJson(Long id, Instant utcDate, String status, TeamJson homeTeam, TeamJson awayTeam, ScoreJson score) {

        ProviderMatch toProviderMatch(String competitionCode) {
            Integer home = null;
            Integer away = null;
            if (score != null && score.fullTime() != null) {
                home = score.fullTime().home();
                away = score.fullTime().away();
            }
            return new ProviderMatch("fd:match:" + id, competitionCode,
                    homeTeam.toProviderTeam(), awayTeam.toProviderTeam(),
                    utcDate, parseStatus(status), home, away);
        }

        private static MatchStatus parseStatus(String status) {
            try {
                return MatchStatus.valueOf(status);
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("Unknown football-data match status '{}', treating as SCHEDULED", status);
                return MatchStatus.SCHEDULED;
            }
        }
    }

    record ScoreJson(FullTimeJson fullTime) {
    }

    record FullTimeJson(Integer home, Integer away) {
    }
}
