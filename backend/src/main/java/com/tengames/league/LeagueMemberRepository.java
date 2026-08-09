package com.tengames.league;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, Long> {

    boolean existsByLeagueIdAndUserId(Long leagueId, Long userId);

    Optional<LeagueMember> findByLeagueIdAndUserId(Long leagueId, Long userId);

    long countByLeagueId(Long leagueId);

    @EntityGraph(attributePaths = "league")
    List<LeagueMember> findByUserIdOrderByJoinedAtAsc(Long userId);
}
