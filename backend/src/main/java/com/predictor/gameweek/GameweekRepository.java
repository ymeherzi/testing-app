package com.predictor.gameweek;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameweekRepository extends JpaRepository<Gameweek, Long> {

    Optional<Gameweek> findFirstByStatusInOrderByWindowStartDesc(Collection<Gameweek.Status> statuses);
}
