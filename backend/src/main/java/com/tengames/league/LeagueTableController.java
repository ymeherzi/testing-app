package com.tengames.league;

import com.tengames.common.web.CurrentUser;
import com.tengames.league.LeagueTableService.ScopedTable;
import com.tengames.league.LeagueTableService.Table;
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
        return leagueTableService.globalTable(CurrentUser.id(authentication), Math.max(page, 0), bounded(size));
    }

    @GetMapping("/api/leagues/country/table")
    public ScopedTable countryTable(Authentication authentication,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return leagueTableService.countryTable(CurrentUser.id(authentication), Math.max(page, 0), bounded(size));
    }

    @GetMapping("/api/leagues/club/table")
    public ScopedTable clubTable(Authentication authentication,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return leagueTableService.clubTable(CurrentUser.id(authentication), Math.max(page, 0), bounded(size));
    }

    @GetMapping("/api/leagues/competition/table")
    public ScopedTable competitionTable(Authentication authentication,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return leagueTableService.competitionTable(CurrentUser.id(authentication), Math.max(page, 0), bounded(size));
    }

    private static int bounded(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
