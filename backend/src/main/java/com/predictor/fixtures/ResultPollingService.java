package com.predictor.fixtures;

import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.catalog.MatchStatus;
import com.predictor.scoring.ScoringService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drives results: when any tracked match could be producing a result right
 * now (in play, or due to have kicked off), re-sync a narrow window and
 * score matches that settled. Self-suppresses outside match windows so no
 * API calls are wasted (design §6).
 */
@Service
public class ResultPollingService {

    private static final Logger log = LoggerFactory.getLogger(ResultPollingService.class);

    private static final EnumSet<MatchStatus> POTENTIALLY_ACTIVE = EnumSet.of(
            MatchStatus.SCHEDULED, MatchStatus.TIMED, MatchStatus.IN_PLAY,
            MatchStatus.PAUSED, MatchStatus.SUSPENDED);

    private final MatchRepository matches;
    private final FixtureSyncService syncService;
    private final ScoringService scoringService;
    private final Clock clock;

    public ResultPollingService(MatchRepository matches, FixtureSyncService syncService,
                                ScoringService scoringService, Clock clock) {
        this.matches = matches;
        this.syncService = syncService;
        this.scoringService = scoringService;
        this.clock = clock;
    }

    public void poll() {
        List<Long> activeIds = matches
                .findByStatusInAndKickoffUtcBefore(POTENTIALLY_ACTIVE, clock.instant())
                .stream()
                .map(Match::getId)
                .toList();
        if (activeIds.isEmpty()) {
            return;
        }
        log.debug("Result poll: {} matches in active windows", activeIds.size());
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        syncService.syncAll(today.minusDays(1), today.plusDays(1));
        for (Long matchId : activeIds) {
            Match refreshed = matches.findById(matchId).orElse(null);
            if (refreshed != null && (refreshed.hasResult() || refreshed.getStatus().isVoided())) {
                scoringService.scoreMatch(matchId);
            }
        }
    }
}
