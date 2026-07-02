package com.predictor.league;

import com.predictor.common.web.CurrentUser;
import com.predictor.league.LeagueTableService.Table;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LeagueTableController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LeagueTableService leagueTableService;

    public LeagueTableController(LeagueTableService leagueTableService) {
        this.leagueTableService = leagueTableService;
    }

    @GetMapping("/api/leagues/global/table")
    public Table globalTable(Authentication authentication,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        int boundedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return leagueTableService.globalTable(CurrentUser.id(authentication), Math.max(page, 0), boundedSize);
    }
}
