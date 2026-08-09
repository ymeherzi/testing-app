package com.prono10.prediction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByUserIdAndGameweekFixtureId(Long userId, Long gameweekFixtureId);

    List<Prediction> findByUserIdAndGameweekFixtureGameweekId(Long userId, Long gameweekId);

    List<Prediction> findByGameweekFixtureMatchId(Long matchId);
}
