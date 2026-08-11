package com.tengames.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Notifications go out from a schedule rather than from the publish call: a
 * push service that is slow or down would otherwise hold up, or fail, the
 * admin's publish — and the notification would be lost with it. Here a failed
 * send simply waits for the next tick.
 *
 * <p>Every quarter of an hour costs nothing: these two methods read the
 * database and nothing else. No push service is called unless there is
 * actually someone to tell.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class NotificationJobs {

    private final NotificationService notifications;

    public NotificationJobs(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    public void announceAndRemind() {
        notifications.notifyOpenedRounds();
        notifications.sendReminders();
    }
}
