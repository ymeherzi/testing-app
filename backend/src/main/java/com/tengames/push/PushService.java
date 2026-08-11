package com.tengames.push;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Subscriptions, and delivery to every device a player has registered.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final PushSender sender;
    private final PushProperties properties;
    private final TransactionTemplate pruneTx;
    private final Clock clock;

    public PushService(PushSubscriptionRepository subscriptions, PushSender sender,
                       PushProperties properties, PlatformTransactionManager txManager, Clock clock) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
        this.pruneTx = new TransactionTemplate(txManager);
        this.pruneTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** The key the browser needs before it can subscribe. */
    public String publicKey() {
        return properties.publicKey();
    }

    @Transactional
    public void subscribe(long userId, String endpoint, String p256dh, String auth) {
        subscriptions.findByEndpoint(endpoint)
                .ifPresentOrElse(
                        existing -> existing.refresh(userId, p256dh, auth, clock.instant()),
                        () -> subscriptions.save(
                                new PushSubscription(userId, endpoint, p256dh, auth, clock.instant())));
    }

    @Transactional
    public void unsubscribe(long userId, String endpoint) {
        // scoped to the caller: an endpoint is not a secret worth trusting
        subscriptions.deleteByEndpointAndUserId(endpoint, userId);
    }

    @Transactional(readOnly = true)
    public boolean isSubscribed(long userId) {
        return subscriptions.existsByUserId(userId);
    }

    /** Everyone reachable: having a subscription is the consent. */
    @Transactional(readOnly = true)
    public List<Long> subscribedUserIds() {
        return subscriptions.subscribedUserIds();
    }

    /**
     * Delivers to every device of one player.
     *
     * @return true when at least one device took it — the caller records the
     *         notification only then, so a total failure is retried later
     */
    public boolean sendToUser(long userId, PushMessage message) {
        boolean delivered = false;
        for (PushSubscription subscription : subscriptions.findByUserId(userId)) {
            try {
                sender.send(subscription, message);
                delivered = true;
            } catch (PushSubscriptionGoneException e) {
                // The device is gone for good: keeping the row would mean
                // failing forever. In its own transaction, because the caller
                // rolls back when nothing was delivered — which would undo this
                // deletion too, and the dead device would be retried on every
                // single tick.
                pruneTx.executeWithoutResult(status -> subscriptions.deleteById(subscription.getId()));
                log.info("Removed dead push subscription {} for user {}", subscription.getId(), userId);
            } catch (RuntimeException e) {
                log.warn("Push to subscription {} failed: {}", subscription.getId(), e.getMessage());
            }
        }
        return delivered;
    }
}
