package com.tengames.fixtures;

import com.tengames.fixtures.FixtureSyncService.SyncSummary;
import com.tengames.fixtures.espn.EspnLiveScoreService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminSyncController {

    private final FixtureSyncService syncService;
    private final EspnLiveScoreService liveScoreService;
    /** Absent when ESPN is switched off, as it is in dev and tests. */
    private final org.springframework.beans.factory.ObjectProvider<com.tengames.fixtures.espn.EspnCupImporter>
            cupImporter;
    private final java.time.Clock clock;

    public AdminSyncController(FixtureSyncService syncService, EspnLiveScoreService liveScoreService,
                               org.springframework.beans.factory.ObjectProvider<
                                       com.tengames.fixtures.espn.EspnCupImporter> cupImporter,
                               java.time.Clock clock) {
        this.syncService = syncService;
        this.liveScoreService = liveScoreService;
        this.cupImporter = cupImporter;
        this.clock = clock;
    }

    /**
     * Pulls the league calendars, then the cups the provider's free tier
     * omits. Both in one action: an editor pressing "sync" wants the pool
     * complete, not partly complete.
     */
    @PostMapping("/api/admin/sync/fixtures")
    public SyncSummary syncFixtures() {
        SyncSummary summary = syncService.syncAll();
        cupImporter.ifAvailable(importer -> {
            java.time.LocalDate today = java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
            importer.importCups(today.minusDays(7), today.plusDays(30));
        });
        return summary;
    }

    @PostMapping("/api/admin/sync/livescores")
    public EspnLiveScoreService.PollSummary syncLiveScores() {
        return liveScoreService.poll();
    }
}
