package com.tengames.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    public AdminNotificationController(NotificationService notifications,
                                       com.tengames.push.PushService push) {
        this.notifications = notifications;
        this.push = push;
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

    /** How many players would hear an announcement sent right now. */
    @GetMapping("/audience")
    public Map<String, Integer> audience() {
        return Map.of("subscribers", push.subscribedUserIds().size());
    }
}
