package com.predictor.fixtures;

import com.predictor.TestcontainersConfiguration;
import com.predictor.catalog.MatchRepository;
import com.predictor.catalog.TeamRepository;
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
}
