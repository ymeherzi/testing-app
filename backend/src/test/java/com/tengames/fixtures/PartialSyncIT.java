package com.tengames.fixtures;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.MatchRepository;
import com.tengames.fixtures.seed.SeedFixtureProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The free tier answers 429 as soon as the minute's allowance is spent, and
 * one of those used to abort the entire sync: everything already fetched was
 * rolled back, and the ESPN cup import that ran afterwards never happened —
 * which is why the season's opening finals were missing from the pool with no
 * error anyone would notice.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PartialSyncIT.Fixtures.class})
@ActiveProfiles("test")
class PartialSyncIT {

    /** A provider that refuses exactly one competition, as a rate limit does. */
    static class FlakyProvider implements FixtureProvider {

        private final FixtureProvider delegate;
        final List<String> attempted = new CopyOnWriteArrayList<>();
        volatile String refuse = "PD";

        FlakyProvider(FixtureProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<ProviderTeam> fetchTeams(String competitionCode) {
            attempted.add(competitionCode);
            if (competitionCode.equals(refuse)) {
                throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests", org.springframework.http.HttpHeaders.EMPTY,
                        "{\"message\":\"You reached your request limit.\"}".getBytes(), null);
            }
            return delegate.fetchTeams(competitionCode);
        }

        @Override
        public List<ProviderMatch> fetchMatches(String competitionCode, LocalDate from, LocalDate to) {
            return delegate.fetchMatches(competitionCode, from, to);
        }
    }

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        FlakyProvider flakyProvider(SeedFixtureProvider seed) {
            return new FlakyProvider(seed);
        }
    }

    @Autowired
    private FlakyProvider provider;

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private CompetitionRepository competitions;

    @Autowired
    private MatchRepository matches;

    @Test
    void oneRefusedCompetitionCostsOnlyThatCompetition() {
        provider.attempted.clear();
        provider.refuse = "PD";

        FixtureSyncService.SyncSummary summary = syncService.syncAll();

        // the refusal is reported rather than thrown away…
        assertThat(summary.failed()).containsExactly("PD");
        // …every other competition was still attempted after it…
        Set<String> backed = competitions.findAll().stream()
                .filter(c -> c.getProviderRef() != null)
                .map(Competition::getCode)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(provider.attempted).containsAll(backed);
        // …and their fixtures survived, instead of being rolled back with it
        assertThat(summary.matchesUpserted()).isPositive();
        assertThat(matches.count()).isPositive();
    }

    @Test
    void aCleanRunReportsNoFailures() {
        provider.refuse = "none";

        assertThat(syncService.syncAll().failed()).isEmpty();
    }
}
