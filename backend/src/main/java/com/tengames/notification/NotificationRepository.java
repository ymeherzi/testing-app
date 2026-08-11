package com.tengames.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n.userId from Notification n where n.gameweekId = :gameweekId and n.kind = :kind")
    List<Long> userIdsNotified(long gameweekId, Notification.Kind kind);

    long countBySentAtAfter(Instant since);

    boolean existsByUserIdAndGameweekIdAndKind(Long userId, Long gameweekId, Notification.Kind kind);

    List<Notification> findByGameweekIdAndKindIn(Long gameweekId, Collection<Notification.Kind> kinds);
}
