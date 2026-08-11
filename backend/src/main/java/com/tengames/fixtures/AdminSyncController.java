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
     * Pulls the cups the provider's free tier omits, then the league
     * calendars. Both in one action: an editor pressing "sync" wants the pool
     * complete, not partly complete.
     *
     * <p>The cups go first on purpose. They used to run afterwards, so any
     * failure in the league sync — a 429 from the free tier was enough — meant
     * the Community Shield and the Trophée des Champions were never fetched at
     * all, and the season's opening finals simply did not exist to choose
     * from. They come from a different service that has no such limit, so
     * nothing about them should depend on football-data answering.
     */
    @PostMapping("/api/admin/sync/fixtures")
    public SyncSummary syncFixtures() {
        cupImporter.ifAvailable(importer -> {
            java.time.LocalDate today = java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
            importer.importCups(today.minusDays(7), today.plusDays(30));
        });
        return syncService.syncAll();
    }

    @PostMapping("/api/admin/sync/livescores")
    public EspnLiveScoreService.PollSummary syncLiveScores() {
        return liveScoreService.poll();
    }
}
