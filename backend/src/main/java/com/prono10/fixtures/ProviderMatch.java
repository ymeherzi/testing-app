package com.prono10.fixtures;

import com.prono10.catalog.MatchStatus;
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
