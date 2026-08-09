package com.tengames.fixtures.espn;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Live-score polling for manual fixtures. Enabled by default (the poll
 * self-suppresses when no manual match is in its window); disabled in dev
 * and tests via app.espn.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.espn.enabled", havingValue = "true", matchIfMissing = true)
public class EspnJobs {

    private final EspnLiveScoreService liveScoreService;

    public EspnJobs(EspnLiveScoreService liveScoreService) {
        this.liveScoreService = liveScoreService;
    }

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT30S")
    public void pollLiveScores() {
        liveScoreService.poll();
    }
}
