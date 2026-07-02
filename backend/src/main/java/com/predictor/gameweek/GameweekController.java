package com.predictor.gameweek;

import com.predictor.common.web.CurrentUser;
import com.predictor.gameweek.GameweekDtos.GameweekView;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gameweeks")
public class GameweekController {

    private final GameweekService gameweekService;

    public GameweekController(GameweekService gameweekService) {
        this.gameweekService = gameweekService;
    }

    @GetMapping("/current")
    public GameweekView current(Authentication authentication) {
        return gameweekService.currentForUser(CurrentUser.id(authentication));
    }

    @GetMapping("/{id}")
    public GameweekView byId(@PathVariable long id, Authentication authentication) {
        return gameweekService.byIdForUser(id, CurrentUser.id(authentication));
    }
}
