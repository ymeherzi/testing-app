package com.tengames.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unsubscribing, from the link at the bottom of every email.
 *
 * <p>A POST and not a GET, even though the link in the mail is a GET: virus
 * scanners and link previews fetch every URL in a message, and would quietly
 * unsubscribe people who never clicked anything. The link opens a page, the
 * page calls this.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    public record UnsubscribeRequest(@NotBlank String u, @NotBlank String t) {
    }

    @PostMapping("/unsubscribe")
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        notifications.unsubscribe(request.u(), request.t());
    }
}
