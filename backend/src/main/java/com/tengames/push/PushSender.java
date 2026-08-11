package com.tengames.push;

/** Port for delivering one message to one subscribed device. */
public interface PushSender {

    /**
     * @throws PushSubscriptionGoneException when the push service says the
     *         subscription no longer exists, so the caller can forget it
     */
    void send(PushSubscription subscription, PushMessage message);
}
