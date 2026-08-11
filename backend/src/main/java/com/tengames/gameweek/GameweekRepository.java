package com.tengames.gameweek;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameweekRepository extends JpaRepository<Gameweek, Long> {

    Optional<Gameweek> findFirstByStatusInOrderByWindowStartDesc(Collection<Gameweek.Status> statuses);

    Optional<Gameweek> findBySeasonAndWeekIndex(String season, int weekIndex);

    /** The gameweek that is current or next relative to the given instant. */
    Optional<Gameweek> findFirstByWindowEndAfterOrderByWindowStartAsc(Instant now);
}
