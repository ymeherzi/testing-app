package com.tengames.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
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

    public AdminNotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    public record AnnounceRequest(
            @NotBlank @Size(max = 60) String title,
            @NotBlank @Size(max = 160) String body) {
    }

    /** Pressing this twice reaches nobody twice; the count says how many it woke. */
    @PostMapping("/launch")
    public Map<String, Integer> announceLaunch(@Valid @RequestBody AnnounceRequest request) {
        return Map.of("sent", notifications.announceSeasonLaunch(request.title(), request.body()));
    }
}
