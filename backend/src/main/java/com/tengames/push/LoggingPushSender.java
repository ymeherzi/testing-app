package com.tengames.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback when no VAPID key is configured, so local development and the test
 * suite behave without one. It logs what would have been shown — a push
 * message carries no secret, unlike a login code.
 */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(PushSubscription subscription, PushMessage message) {
        log.info("No VAPID keys configured — '{}' would have gone to user {} ({})",
                message.title(), subscription.getUserId(), message.body());
    }
}
