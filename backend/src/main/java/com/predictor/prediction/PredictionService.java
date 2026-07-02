package com.predictor.prediction;

import com.predictor.gameweek.Gameweek;
import com.predictor.gameweek.GameweekDtos.PredictionView;
import com.predictor.gameweek.GameweekFixture;
import com.predictor.gameweek.GameweekFixtureRepository;
import com.predictor.gameweek.GameweekService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PredictionService {

    private final PredictionRepository predictions;
    private final GameweekFixtureRepository fixtures;
    private final GameweekService gameweekService;
    private final Clock clock;

    public PredictionService(PredictionRepository predictions, GameweekFixtureRepository fixtures,
                             GameweekService gameweekService, Clock clock) {
        this.predictions = predictions;
        this.fixtures = fixtures;
        this.gameweekService = gameweekService;
        this.clock = clock;
    }

    /**
     * Create or update the caller's prediction for a fixture. The lock is
     * server-side truth (design §2.2): compared against the latest known
     * kickoff inside the transaction, so client clocks are irrelevant.
     */
    @Transactional
    public PredictionView upsert(long userId, long gameweekId, long fixtureId, int homeGoals, int awayGoals) {
        GameweekFixture fixture = fixtures.findByIdAndGameweekId(fixtureId, gameweekId)
                .filter(f -> f.getGameweek().getStatus() != Gameweek.Status.DRAFT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fixture not found"));
        Instant now = clock.instant();
        if (gameweekService.isLocked(fixture.getMatch(), now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Predictions are locked for this match");
        }
        Prediction prediction = predictions.findByUserIdAndGameweekFixtureId(userId, fixtureId)
                .orElseGet(() -> new Prediction(userId, fixture, homeGoals, awayGoals, now));
        prediction.updateScoreline(homeGoals, awayGoals, now);
        return PredictionView.from(predictions.save(prediction));
    }
}
