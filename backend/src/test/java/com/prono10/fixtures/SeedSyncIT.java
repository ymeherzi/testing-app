package com.prono10.fixtures;

import com.prono10.TestcontainersConfiguration;
import com.prono10.catalog.MatchRepository;
import com.prono10.catalog.TeamRepository;
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

    @Test
    void seedSyncIsIdempotent() {
        FixtureSyncService.SyncSummary first = syncService.syncAll();
        long teamCount = teams.count();
        long matchCount = matches.count();

        // 6 competitions × 6 generated matches
        assertThat(first.matchesUpserted()).isEqualTo(36);
        assertThat(matchCount).isEqualTo(36);
        assertThat(teamCount).isGreaterThan(0);

        syncService.syncAll();
        assertThat(teams.count()).isEqualTo(teamCount);
        assertThat(matches.count()).isEqualTo(matchCount);
    }

    @Test
    void persistsScoresAndStatusesFromTheProvider() {
        syncService.syncAll();

        // seed slot 1 is always a finished 2-1 (regression: mutations after
        // save() must happen inside the sync transaction to be flushed)
        var finished = matches.findByProviderRef("seed:PL:1").orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(com.prono10.catalog.MatchStatus.FINISHED);
        assertThat(finished.getHomeScore()).isEqualTo(2);
        assertThat(finished.getAwayScore()).isEqualTo(1);

        var inPlay = matches.findByProviderRef("seed:PL:2").orElseThrow();
        assertThat(inPlay.getStatus()).isEqualTo(com.prono10.catalog.MatchStatus.IN_PLAY);
        assertThat(inPlay.getHomeScore()).isZero();
    }
}
