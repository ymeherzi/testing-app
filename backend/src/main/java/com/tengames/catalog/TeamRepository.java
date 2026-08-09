package com.tengames.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByProviderRef(String providerRef);

    Optional<Team> findFirstByNameIgnoreCase(String name);

    List<Team> findAllByOrderByNameAsc();
}
