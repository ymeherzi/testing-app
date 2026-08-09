package com.prono10.league;

import com.prono10.common.web.CurrentUser;
import com.prono10.league.LeagueDtos.CreateLeagueRequest;
import com.prono10.league.LeagueDtos.JoinLeagueRequest;
import com.prono10.league.LeagueDtos.LeagueDetail;
import com.prono10.league.LeagueDtos.LeagueSummary;
import jakarta.validation.Valid;
import java.util.List;
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

    @GetMapping("/{id:\\d+}")
    public LeagueDetail detail(Authentication authentication, @PathVariable long id) {
        return leagueService.detail(CurrentUser.id(authentication), id);
    }

    @PostMapping("/{id:\\d+}/regenerate-code")
    public LeagueDetail regenerateCode(Authentication authentication, @PathVariable long id) {
        return leagueService.regenerateCode(CurrentUser.id(authentication), id);
    }

    @DeleteMapping("/{id:\\d+}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication authentication, @PathVariable long id) {
        leagueService.leave(CurrentUser.id(authentication), id);
    }
}
