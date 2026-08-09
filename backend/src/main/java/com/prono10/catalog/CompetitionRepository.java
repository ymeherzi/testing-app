package com.prono10.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    Optional<Competition> findByCode(String code);
}
