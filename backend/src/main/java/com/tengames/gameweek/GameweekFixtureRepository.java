package com.tengames.gameweek;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameweekFixtureRepository extends JpaRepository<GameweekFixture, Long> {

    @EntityGraph(attributePaths = {"match", "match.competition", "match.homeTeam", "match.awayTeam"})
    List<GameweekFixture> findByGameweekIdOrderByMatchKickoffUtcAsc(Long gameweekId);

    Optional<GameweekFixture> findByIdAndGameweekId(Long id, Long gameweekId);

    List<GameweekFixture> findByMatchId(Long matchId);

    void deleteByGameweekId(Long gameweekId);

    long countByGameweekId(Long gameweekId);
}
