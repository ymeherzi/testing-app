package com.tengames.fixtures;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SeedSyncIT {

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private MatchRepository matches;

    /** Rows this provider owns; other suites share the database and add their own. */
    private long seededMatches() {
        return matches.findAll().stream()
                .filter(match -> match.getProviderRef() != null && match.getProviderRef().startsWith("seed:"))
                .count();
    }

    @Test
    void seedSyncIsIdempotent() {
        FixtureSyncService.SyncSummary first = syncService.syncAll();
        long teamCount = teams.count();
        long matchCount = seededMatches();

        // 6 competitions × 6 generated matches
        assertThat(first.matchesUpserted()).isEqualTo(36);
        assertThat(matchCount).isEqualTo(36);
        assertThat(teamCount).isGreaterThan(0);
        assertThat(first.failed()).isEmpty();

        // running it twice must not double anything — the point of the test.
        // Counting only what this provider owns, because a total over the
        // whole table makes this fail the day another suite stores a match.
        syncService.syncAll();
        assertThat(teams.count()).isEqualTo(teamCount);
        assertThat(seededMatches()).isEqualTo(matchCount);
    }

    @Test
    void persistsScoresAndStatusesFromTheProvider() {
        syncService.syncAll();

        // seed slot 1 is always a finished 2-1 (regression: mutations after
        // save() must happen inside the sync transaction to be flushed)
        var finished = matches.findByProviderRef("seed:PL:1").orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(com.tengames.catalog.MatchStatus.FINISHED);
        assertThat(finished.getHomeScore()).isEqualTo(2);
        assertThat(finished.getAwayScore()).isEqualTo(1);

        var inPlay = matches.findByProviderRef("seed:PL:2").orElseThrow();
        assertThat(inPlay.getStatus()).isEqualTo(com.tengames.catalog.MatchStatus.IN_PLAY);
        assertThat(inPlay.getHomeScore()).isZero();
    }
}
