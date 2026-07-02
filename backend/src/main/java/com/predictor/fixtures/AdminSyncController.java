package com.predictor.fixtures;

import com.predictor.fixtures.FixtureSyncService.SyncSummary;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminSyncController {

    private final FixtureSyncService syncService;

    public AdminSyncController(FixtureSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/api/admin/sync/fixtures")
    public SyncSummary syncFixtures() {
        return syncService.syncAll();
    }
}
