package com.prono10.prediction;

import com.prono10.common.web.CurrentUser;
import com.prono10.gameweek.GameweekDtos.PredictionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    public record PredictionRequest(
            @Min(0) @Max(20) int homeGoals,
            @Min(0) @Max(20) int awayGoals) {
    }

    @PutMapping("/api/gameweeks/{gameweekId}/fixtures/{fixtureId}/prediction")
    public PredictionView upsert(@PathVariable long gameweekId, @PathVariable long fixtureId,
                                 Authentication authentication, @Valid @RequestBody PredictionRequest request) {
        return predictionService.upsert(CurrentUser.id(authentication), gameweekId, fixtureId,
                request.homeGoals(), request.awayGoals());
    }
}
