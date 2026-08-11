package com.tengames.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n.userId from Notification n where n.gameweekId = :gameweekId and n.kind = :kind")
    List<Long> userIdsNotified(long gameweekId, Notification.Kind kind);

    /** The announcements that belong to no round, such as the season launch. */
    @Query("select n.userId from Notification n where n.gameweekId is null and n.kind = :kind")
    List<Long> userIdsAnnounced(Notification.Kind kind);

    boolean existsByUserIdAndGameweekIdAndKind(Long userId, Long gameweekId, Notification.Kind kind);

    boolean existsByUserIdAndGameweekIdIsNullAndKind(Long userId, Notification.Kind kind);
}
