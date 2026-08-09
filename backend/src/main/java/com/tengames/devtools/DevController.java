package com.tengames.devtools;

import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.gameweek.GameweekDtos.MatchView;
import com.tengames.scoring.ScoringService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-profile-only simulation endpoints (admin role required, see
 * SecurityConfig): set results and move kickoffs without a data provider.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevController {

    private final MatchRepository matches;
    private final ScoringService scoringService;

    public DevController(MatchRepository matches, ScoringService scoringService) {
        this.matches = matches;
        this.scoringService = scoringService;
    }

    public record ResultRequest(@Min(0) @Max(20) int homeScore, @Min(0) @Max(20) int awayScore) {
    }

    public record KickoffRequest(@NotNull Instant kickoffUtc) {
    }

    @PostMapping("/matches/{id}/result")
    @Transactional
    public MatchView setResult(@PathVariable long id, @Valid @RequestBody ResultRequest request) {
        Match match = matches.findById(id).orElseThrow();
        match.setScore(request.homeScore(), request.awayScore());
        match.setStatus(MatchStatus.FINISHED);
        matches.flush();
        scoringService.scoreMatch(id);
        return MatchView.from(match);
    }

    @PostMapping("/matches/{id}/kickoff")
    @Transactional
    public MatchView setKickoff(@PathVariable long id, @Valid @RequestBody KickoffRequest request) {
        Match match = matches.findById(id).orElseThrow();
        match.setKickoffUtc(request.kickoffUtc());
        match.setStatus(MatchStatus.TIMED);
        match.setScore(null, null);
        return MatchView.from(match);
    }
}
