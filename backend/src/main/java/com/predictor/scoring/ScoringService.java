package com.predictor.scoring;

import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.gameweek.Gameweek;
import com.predictor.gameweek.GameweekFixture;
import com.predictor.gameweek.GameweekFixtureRepository;
import com.predictor.prediction.Prediction;
import com.predictor.prediction.PredictionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final MatchRepository matches;
    private final PredictionRepository predictions;
    private final GameweekFixtureRepository fixtures;
    private final Clock clock;

    public ScoringService(MatchRepository matches, PredictionRepository predictions,
                          GameweekFixtureRepository fixtures, Clock clock) {
        this.matches = matches;
        this.predictions = predictions;
        this.fixtures = fixtures;
        this.clock = clock;
    }

    /**
     * (Re)score every prediction on a match. Points are always recomputed
     * from the current result and overwritten — never incremented — so this
     * is idempotent and safe to re-run after result corrections (design §2.3).
     * Voided matches (postponed/cancelled) clear points so they count for
     * nobody. When all of a gameweek's matches are settled, it flips to SCORED.
     */
    @Transactional
    public void scoreMatch(long matchId) {
        Match match = matches.findById(matchId).orElseThrow();
        List<Prediction> affected = predictions.findByGameweekFixtureMatchId(matchId);
        Instant now = clock.instant();
        if (match.hasResult()) {
            for (Prediction prediction : affected) {
                int points = ScoringEngine.score(prediction.getHomeGoals(), prediction.getAwayGoals(),
                        match.getHomeScore(), match.getAwayScore());
                prediction.setPoints(points, now);
            }
            log.info("Scored match {}: {} predictions against result {}-{}",
                    matchId, affected.size(), match.getHomeScore(), match.getAwayScore());
        } else if (match.getStatus().isVoided()) {
            for (Prediction prediction : affected) {
                prediction.setPoints(null, null);
            }
            log.info("Voided match {}: cleared points on {} predictions", matchId, affected.size());
        } else {
            return; // nothing to do until the match settles
        }
        markSettledGameweeks(matchId);
    }

    private void markSettledGameweeks(long matchId) {
        for (GameweekFixture fixture : fixtures.findByMatchId(matchId)) {
            Gameweek gameweek = fixture.getGameweek();
            if (gameweek.getStatus() != Gameweek.Status.PUBLISHED) {
                continue;
            }
            boolean allSettled = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweek.getId()).stream()
                    .allMatch(f -> f.getMatch().hasResult() || f.getMatch().getStatus().isVoided());
            if (allSettled) {
                gameweek.setStatus(Gameweek.Status.SCORED);
                log.info("Gameweek {} fully settled, marked SCORED", gameweek.getId());
            }
        }
    }
}
