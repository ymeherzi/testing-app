package com.tengames.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The club catalogue, and searching it.
 *
 * <p>Searching happens here rather than in the browser for one reason: the
 * normalisation that lets "atletico" find Atlético and "munich" find the club
 * football-data calls "FC Bayern München" already exists, in
 * {@link ClubNames}, aliases and all. A second implementation in TypeScript
 * would drift from it the first time an alias is added.
 */
@RestController
public class TeamController {

    /** Enough to choose from on a phone; more is a list nobody reads. */
    private static final int MAX_RESULTS = 50;

    private final TeamRepository teams;

    public TeamController(TeamRepository teams) {
        this.teams = teams;
    }

    public record TeamResponse(Long id, String name, String shortName, String crestUrl,
                               String tla, Long competitionId) {

        public static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(), team.getShortName(), team.getCrestUrl(),
                    team.getTla(), team.getPrimaryCompetitionId());
        }
    }

    /**
     * Clubs matching a query, optionally within one competition.
     *
     * <p>With no query the whole catalogue still comes back: several hundred
     * clubs, which is exactly the list the picker exists to avoid, but callers
     * that genuinely want everything (and the older clients) keep working.
     */
    @GetMapping("/api/teams")
    public List<TeamResponse> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Long competition,
                                   @RequestParam(required = false) Long id,
                                   @RequestParam(required = false) Integer limit) {
        if (id != null) {
            // one club by id: what the profile needs to show the club already
            // chosen without making the player search for it
            return teams.findById(id).map(TeamResponse::from).map(List::of).orElseGet(List::of);
        }
        List<Team> candidates = competition == null
                ? teams.findAllByOrderByNameAsc()
                : teams.findByPrimaryCompetitionIdOrderByNameAsc(competition);
        String query = q == null ? "" : q.trim();
        if (!query.isEmpty()) {
            candidates = candidates.stream().filter(team -> matches(team, query)).sorted(bestFirst(query)).toList();
        }
        int cap = limit == null || limit < 1 ? MAX_RESULTS : Math.min(limit, MAX_RESULTS * 10);
        return candidates.stream().limit(cap).map(TeamResponse::from).toList();
    }

    private static boolean matches(Team team, String query) {
        if (team.getTla() != null && team.getTla().equalsIgnoreCase(query)) {
            return true;
        }
        String needle = ClubNames.key(query);
        if (needle.isEmpty()) {
            // the query was nothing but stripped words ("de", "fc"): fall back
            // to the raw text rather than matching every club in the database
            return team.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        }
        // every known spelling of this club, so "munich" reaches "München"
        return ClubNames.spellings(team.getName()).stream().anyMatch(spelling -> spelling.contains(needle))
               || (team.getShortName() != null && ClubNames.key(team.getShortName()).contains(needle));
    }

    /** A club whose name starts with what was typed comes before one that merely contains it. */
    private static Comparator<Team> bestFirst(String query) {
        String needle = ClubNames.key(query);
        return Comparator.comparing((Team team) -> !ClubNames.key(team.getName()).startsWith(needle))
                .thenComparing(Team::getName);
    }
}
