package com.tengames.fixtures;

import com.tengames.fixtures.FixtureSyncService.SyncSummary;
import com.tengames.fixtures.espn.EspnLiveScoreService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminSyncController {

    private final FixtureSyncService syncService;
    private final EspnLiveScoreService liveScoreService;

    public AdminSyncController(FixtureSyncService syncService, EspnLiveScoreService liveScoreService) {
        this.syncService = syncService;
        this.liveScoreService = liveScoreService;
    }

    @PostMapping("/api/admin/sync/fixtures")
    public SyncSummary syncFixtures() {
        return syncService.syncAll();
    }

    @PostMapping("/api/admin/sync/livescores")
    public EspnLiveScoreService.PollSummary syncLiveScores() {
        return liveScoreService.poll();
    }
}
