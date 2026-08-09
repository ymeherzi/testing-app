package com.tengames.league;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueRepository extends JpaRepository<League, Long> {

    Optional<League> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
