package com.predictor.catalog;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByProviderRef(String providerRef);

    @EntityGraph(attributePaths = {"competition", "homeTeam", "awayTeam"})
    List<Match> findByKickoffUtcBetweenOrderByKickoffUtcAsc(Instant from, Instant to);

    @EntityGraph(attributePaths = {"competition", "homeTeam", "awayTeam"})
    List<Match> findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(String code, Instant from, Instant to);

    /** Matches whose result may change right now: in play, or due to have kicked off. */
    List<Match> findByStatusInAndKickoffUtcBefore(Collection<MatchStatus> statuses, Instant kickoffBefore);

    /** Same window with teams eagerly loaded (used outside transactions). */
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Match> findWithTeamsByStatusInAndKickoffUtcBefore(Collection<MatchStatus> statuses, Instant kickoffBefore);
}
