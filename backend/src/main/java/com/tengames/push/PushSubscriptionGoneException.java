package com.tengames.push;

/**
 * The device unsubscribed, cleared its data, or the browser rotated the
 * endpoint. Retrying will never work; the row should go.
 */
public class PushSubscriptionGoneException extends RuntimeException {

    public PushSubscriptionGoneException(String message) {
        super(message);
    }
}
