package com.prono10.fixtures;

import java.time.LocalDate;
import java.util.List;

/**
 * Port for external fixture/result data (docs/DESIGN.md §6). Implementations:
 * football-data.org for real data, a deterministic seed provider for offline
 * dev/demo. Selected via {@code app.fixtures.provider}.
 */
public interface FixtureProvider {

    List<ProviderTeam> fetchTeams(String competitionCode);

    List<ProviderMatch> fetchMatches(String competitionCode, LocalDate from, LocalDate to);
}
