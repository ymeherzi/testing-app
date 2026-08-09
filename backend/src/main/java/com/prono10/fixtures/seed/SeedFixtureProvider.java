package com.prono10.fixtures.seed;

import com.prono10.catalog.MatchStatus;
import com.prono10.fixtures.FixtureProvider;
import com.prono10.fixtures.ProviderMatch;
import com.prono10.fixtures.ProviderTeam;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline data source: deterministic fixtures generated relative to the
 * current clock so the demo always contains a finished match, a live match,
 * and upcoming matches at +2h/+1d/+2d/+3d regardless of when it runs.
 */
@Component
@ConditionalOnProperty(name = "app.fixtures.provider", havingValue = "seed", matchIfMissing = true)
public class SeedFixtureProvider implements FixtureProvider {

    private static final Map<String, List<String>> TEAMS_BY_COMPETITION = Map.of(
            "PL", List.of("Arsenal", "Manchester City", "Liverpool", "Chelsea",
                    "Manchester United", "Tottenham Hotspur", "Newcastle United", "Aston Villa"),
            "PD", List.of("Real Madrid", "Barcelona", "Atlético Madrid", "Athletic Club",
                    "Real Sociedad", "Villarreal", "Real Betis", "Sevilla"),
            "SA", List.of("Inter", "AC Milan", "Juventus", "Napoli",
                    "Roma", "Lazio", "Atalanta", "Fiorentina"),
            "BL1", List.of("Bayern München", "Borussia Dortmund", "RB Leipzig", "Bayer Leverkusen",
                    "VfB Stuttgart", "Eintracht Frankfurt", "SC Freiburg", "VfL Wolfsburg"),
            "FL1", List.of("Paris Saint-Germain", "Marseille", "Monaco", "Lyon",
                    "Lille", "Nice", "Lens", "Rennes"),
            "CL", List.of("Real Madrid", "Manchester City", "Bayern München", "Paris Saint-Germain",
                    "Inter", "Arsenal", "Barcelona", "Borussia Dortmund"));

    private final Clock clock;

    public SeedFixtureProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<ProviderTeam> fetchTeams(String competitionCode) {
        List<String> names = TEAMS_BY_COMPETITION.getOrDefault(competitionCode, List.of());
        List<ProviderTeam> teams = new ArrayList<>();
        for (String name : names) {
            teams.add(team(name));
        }
        return teams;
    }

    @Override
    public List<ProviderMatch> fetchMatches(String competitionCode, LocalDate from, LocalDate to) {
        List<String> names = TEAMS_BY_COMPETITION.getOrDefault(competitionCode, List.of());
        if (names.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        List<ProviderMatch> matches = new ArrayList<>();
        // Fixed pairings (i, i+1); kickoff offsets give every demo state at once.
        record Slot(Duration offset, MatchStatus status, Integer home, Integer away) {
        }
        List<Slot> slots = List.of(
                new Slot(Duration.ofDays(-1), MatchStatus.FINISHED, 2, 1),
                new Slot(Duration.ofMinutes(-30), MatchStatus.IN_PLAY, 0, 0),
                new Slot(Duration.ofHours(2), MatchStatus.TIMED, null, null),
                new Slot(Duration.ofDays(1), MatchStatus.TIMED, null, null),
                new Slot(Duration.ofDays(2), MatchStatus.TIMED, null, null),
                new Slot(Duration.ofDays(3), MatchStatus.TIMED, null, null));
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            String home = names.get((2 * i) % names.size());
            String away = names.get((2 * i + 1) % names.size());
            matches.add(new ProviderMatch(
                    "seed:%s:%d".formatted(competitionCode, i + 1),
                    competitionCode,
                    team(home),
                    team(away),
                    now.plus(slot.offset()).truncatedTo(java.time.temporal.ChronoUnit.MINUTES),
                    slot.status(),
                    slot.home(),
                    slot.away()));
        }
        return matches;
    }

    private ProviderTeam team(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return new ProviderTeam("seed:team:" + slug, name, name, null);
    }
}
