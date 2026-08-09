package com.tengames.fixtures;

import com.tengames.catalog.MatchStatus;
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
