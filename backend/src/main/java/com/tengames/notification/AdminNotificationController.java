package com.tengames.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The season-launch announcement — the one notification with no schedule
 * behind it, because only you know when the season really starts.
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notifications;
    private final com.tengames.push.PushService push;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public AdminNotificationController(NotificationService notifications,
                                       com.tengames.push.PushService push,
                                       org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.notifications = notifications;
        this.push = push;
        this.jdbc = jdbc;
    }

    public record AnnounceRequest(
            @NotBlank @Size(max = 60) String title,
            @NotBlank @Size(max = 160) String body) {
    }

    /**
     * Pressing this twice reaches nobody twice; the count says how many it woke.
     *
     * <p>The audience comes back with it, because "sent to 0" otherwise says
     * two very different things: nobody has notifications on, or everybody has
     * already been told.
     */
    @PostMapping("/launch")
    public Map<String, Integer> announceLaunch(@Valid @RequestBody AnnounceRequest request) {
        int subscribers = push.subscribedUserIds().size();
        return Map.of("sent", notifications.announceSeasonLaunch(request.title(), request.body()),
                "subscribers", subscribers);
    }


    /** One player who would hear an announcement sent right now. */
    public record Listener(java.util.UUID id, String displayName, long devices,
                           java.time.Instant lastNotifiedAt) {
    }

    /**
     * Who would hear an announcement sent right now — by name, not just a
     * count. "Sent to 4" raises the question immediately: which four?
     */
    @GetMapping("/audience")
    public Map<String, Object> audience() {
        List<Listener> listeners = jdbc.sql("""
                        select u.public_id, u.display_name,
                               count(s.id) as devices,
                               (select max(n.sent_at) from notifications n where n.user_id = u.id) as last_notified
                        from users u
                        join push_subscriptions s on s.user_id = u.id
                        group by u.id, u.public_id, u.display_name
                        order by u.display_name""")
                .query((rs, row) -> new Listener(
                        java.util.UUID.fromString(rs.getString("public_id")),
                        rs.getString("display_name"), rs.getLong("devices"),
                        rs.getTimestamp("last_notified") == null
                                ? null
                                : rs.getTimestamp("last_notified").toInstant()))
                .list();
        return Map.of("subscribers", listeners.size(), "listeners", listeners);
    }
}
