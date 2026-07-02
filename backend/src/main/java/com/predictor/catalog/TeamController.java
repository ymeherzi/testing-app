package com.predictor.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamController {

    private final TeamRepository teams;

    public TeamController(TeamRepository teams) {
        this.teams = teams;
    }

    public record TeamResponse(Long id, String name, String shortName, String crestUrl) {

        public static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(), team.getShortName(), team.getCrestUrl());
        }
    }

    @GetMapping("/api/teams")
    public List<TeamResponse> list() {
        return teams.findAllByOrderByNameAsc().stream().map(TeamResponse::from).toList();
    }
}
