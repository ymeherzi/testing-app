package com.tengames.fixtures;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled data jobs (design §6). Disabled by default and in dev/tests via
 * app.jobs.enabled; the admin/dev endpoints trigger the same services
 * manually.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class SyncJobs {

    private final FixtureSyncService syncService;
    private final ResultPollingService resultPollingService;
    /** Absent when ESPN is switched off, as it is in dev and tests. */
    private final org.springframework.beans.factory.ObjectProvider<com.tengames.fixtures.espn.EspnCupImporter>
            cupImporter;
    private final Clock clock;

    public SyncJobs(FixtureSyncService syncService, ResultPollingService resultPollingService,
                    org.springframework.beans.factory.ObjectProvider<com.tengames.fixtures.espn.EspnCupImporter>
                            cupImporter,
                    Clock clock) {
        this.syncService = syncService;
        this.resultPollingService = resultPollingService;
        this.cupImporter = cupImporter;
        this.clock = clock;
    }

    /**
     * Full upcoming-window sync, once a day.
     *
     * <p>Cups first: they come from ESPN, which has no rate limit, and running
     * them after the league sync meant one 429 from football-data's free tier
     * kept the season's opening finals out of the pool for the whole day.
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "UTC")
    public void dailyFixtureSync() {
        cupImporter.ifAvailable(importer -> {
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            importer.importCups(today.minusDays(7), today.plusDays(30));
        });
        syncService.syncAll();
    }

    /**
     * Hourly refresh of the fortnight ahead.
     *
     * <p>Wider than the 48 hours it once covered: an editor composing a round
     * needs the fixtures to be there when they press the button, not at five
     * the next morning. Kickoff changes inside the window are picked up here
     * too, so locks follow a rescheduled match.
     */
    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    public void kickoffRefresh() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        cupImporter.ifAvailable(importer -> importer.importCups(today.minusDays(1), today.plusDays(14)));
        // fixtures only: squad lists change about never, and fetching them
        // hourly for a dozen competitions would spend the whole free-tier
        // allowance on data we already have
        syncService.syncAll(today.minusDays(1), today.plusDays(14), false);
    }

    /** Result polling; self-suppresses when no match window is active. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void pollResults() {
        resultPollingService.poll();
    }
}
