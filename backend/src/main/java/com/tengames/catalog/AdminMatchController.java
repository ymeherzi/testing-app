package com.tengames.catalog;

import com.tengames.gameweek.GameweekDtos.MatchView;
import com.tengames.scoring.ScoringService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manual fixture entry (design doc §6: manual admin mode). Lets the game
 * admin add real matches that no provider covers — friendlies, cups, other
 * leagues — and enter their results, which triggers scoring. Manual rows
 * have no provider_ref, so provider syncs never touch them.
 */
@RestController
@RequestMapping("/api/admin/matches")
public class AdminMatchController {

    private final CompetitionRepository competitions;
    private final TeamRepository teams;
    private final MatchRepository matches;
    private final ScoringService scoringService;

    public AdminMatchController(CompetitionRepository competitions, TeamRepository teams,
                                MatchRepository matches, ScoringService scoringService) {
        this.competitions = competitions;
        this.teams = teams;
        this.matches = matches;
        this.scoringService = scoringService;
    }

    public record CreateMatchRequest(
            @NotBlank @Size(max = 8) String competitionCode,
            @Size(max = 64) String competitionName,
            @NotBlank @Size(max = 128) String homeTeam,
            @NotBlank @Size(max = 128) String awayTeam,
            @NotNull Instant kickoffUtc) {
    }

    public record ResultRequest(@Min(0) @Max(20) int homeScore, @Min(0) @Max(20) int awayScore) {
    }

    @PostMapping
    @Transactional
    public MatchView create(@Valid @RequestBody CreateMatchRequest request) {
        if (request.homeTeam().trim().equalsIgnoreCase(request.awayTeam().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A team cannot play itself");
        }
        Competition competition = competitions.findByCode(request.competitionCode().toUpperCase())
                .orElseGet(() -> competitions.save(new Competition(
                        request.competitionCode().toUpperCase(),
                        request.competitionName() != null && !request.competitionName().isBlank()
                                ? request.competitionName()
                                : request.competitionCode().toUpperCase())));
        Match match = matches.save(new Match(competition,
                resolveTeam(request.homeTeam()), resolveTeam(request.awayTeam()),
                request.kickoffUtc(), MatchStatus.TIMED, null));
        return MatchView.from(match);
    }

    @PostMapping("/{id}/result")
    @Transactional
    public MatchView setResult(@PathVariable long id, @Valid @RequestBody ResultRequest request) {
        Match match = matches.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        match.setScore(request.homeScore(), request.awayScore());
        match.setStatus(MatchStatus.FINISHED);
        matches.flush();
        scoringService.scoreMatch(id);
        return MatchView.from(match);
    }

    private Team resolveTeam(String name) {
        String trimmed = name.trim();
        return teams.findFirstByNameIgnoreCase(trimmed)
                .orElseGet(() -> teams.save(new Team(trimmed, trimmed, null, null)));
    }
}
