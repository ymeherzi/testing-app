package com.predictor.fixtures;

import com.predictor.catalog.MatchStatus;
import java.time.Instant;

public record ProviderMatch(
        String providerRef,
        String competitionCode,
        ProviderTeam homeTeam,
        ProviderTeam awayTeam,
        Instant kickoffUtc,
        MatchStatus status,
        Integer homeScore,
        Integer awayScore) {
}
