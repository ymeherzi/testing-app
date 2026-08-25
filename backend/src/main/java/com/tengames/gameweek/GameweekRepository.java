package com.tengames.gameweek;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameweekRepository extends JpaRepository<Gameweek, Long> {

    Optional<Gameweek> findFirstByStatusInOrderByWindowStartDesc(Collection<Gameweek.Status> statuses);

    Optional<Gameweek> findBySeasonAndWeekIndex(String season, int weekIndex);

    java.util.List<Gameweek> findByStatus(Gameweek.Status status);

    /** The gameweek that is current or next relative to the given instant. */
    Optional<Gameweek> findFirstByWindowEndAfterOrderByWindowStartAsc(Instant now);

    /** The playable round whose window is open right now, latest first if they overlap. */
    Optional<Gameweek> findFirstByStatusInAndWindowStartBeforeAndWindowEndAfterOrderByWindowStartDesc(
            Collection<Gameweek.Status> statuses, Instant before, Instant after);

    /** The next playable round to open. */
    Optional<Gameweek> findFirstByStatusInAndWindowStartAfterOrderByWindowStartAsc(
            Collection<Gameweek.Status> statuses, Instant now);
}
