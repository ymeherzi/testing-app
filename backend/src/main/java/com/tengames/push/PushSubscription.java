package com.tengames.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One device that agreed to be notified.
 *
 * <p>The existence of a row is the consent: there is no separate preference to
 * keep in step with it, and revoking permission in the browser removes the
 * only record we have.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The push service's URL for this device — its identity. */
    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PushSubscription() {
    }

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth, Instant now) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.lastSeenAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * A browser may hand back the same endpoint with fresh keys after it
     * re-subscribes; keeping the row and updating it avoids a duplicate that
     * the unique endpoint would refuse anyway.
     */
    public void refresh(Long userId, String p256dh, String auth, Instant now) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.lastSeenAt = now;
    }
}
