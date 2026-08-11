package com.tengames.push;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(Long userId);

    void deleteByEndpointAndUserId(String endpoint, Long userId);

    /** Everyone who can be reached — the notification jobs' audience. */
    @Query("select distinct s.userId from PushSubscription s")
    List<Long> subscribedUserIds();

    boolean existsByUserId(Long userId);
}
