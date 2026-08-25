package com.tengames.league;

import com.tengames.common.web.CurrentUser;
import com.tengames.league.LeagueDtos.CreateLeagueRequest;
import com.tengames.league.LeagueDtos.JoinLeagueRequest;
import com.tengames.league.LeagueDtos.LeagueDetail;
import com.tengames.league.LeagueDtos.LeagueSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @PostMapping
    public LeagueDetail create(Authentication authentication, @Valid @RequestBody CreateLeagueRequest request) {
        return leagueService.create(CurrentUser.id(authentication), request.name());
    }

    @PostMapping("/join")
    public LeagueDetail join(Authentication authentication, @Valid @RequestBody JoinLeagueRequest request) {
        return leagueService.join(CurrentUser.id(authentication), request.code());
    }

    @GetMapping("/mine")
    public List<LeagueSummary> mine(Authentication authentication) {
        return leagueService.myLeagues(CurrentUser.id(authentication));
    }

    // The pattern keeps /mine and /join out of this route, exactly as the
    // digits-only pattern it replaces did, and answers a malformed id with a
    // 404 rather than a 400.
    @GetMapping("/{id:[0-9a-fA-F-]{36}}")
    public LeagueDetail detail(Authentication authentication, @PathVariable UUID id) {
        return leagueService.detail(CurrentUser.id(authentication), id);
    }

    @PostMapping("/{id:[0-9a-fA-F-]{36}}/regenerate-code")
    public LeagueDetail regenerateCode(Authentication authentication, @PathVariable UUID id) {
        return leagueService.regenerateCode(CurrentUser.id(authentication), id);
    }

    @DeleteMapping("/{id:[0-9a-fA-F-]{36}}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication authentication, @PathVariable UUID id) {
        leagueService.leave(CurrentUser.id(authentication), id);
    }
}
