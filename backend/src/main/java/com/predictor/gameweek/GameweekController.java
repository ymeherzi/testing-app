package com.predictor.gameweek;

import com.predictor.common.web.CurrentUser;
import com.predictor.gameweek.GameweekDtos.GameweekView;
import com.predictor.gameweek.GameweekDtos.PlayerGameweekView;
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

    /** Gameweek history, newest first, with the caller's points per gameweek. */
    @GetMapping
    public java.util.List<GameweekDtos.GameweekSummary> history(Authentication authentication) {
        return gameweekService.history(CurrentUser.id(authentication));
    }

    @GetMapping("/{id:\\d+}")
    public GameweekView byId(@PathVariable long id, Authentication authentication) {
        return gameweekService.byIdForUser(id, CurrentUser.id(authentication));
    }

    /** Another player's picks for this gameweek (locked matches only). */
    @GetMapping("/{id:\\d+}/players/{playerId:\\d+}")
    public PlayerGameweekView player(@PathVariable long id, @PathVariable long playerId) {
        return gameweekService.playerView(id, playerId);
    }

    /** Convenience for the current gameweek. */
    @GetMapping("/current/players/{playerId:\\d+}")
    public PlayerGameweekView playerCurrent(@PathVariable long playerId, Authentication authentication) {
        long gameweekId = gameweekService.currentForUser(CurrentUser.id(authentication)).id();
        return gameweekService.playerView(gameweekId, playerId);
    }
}
