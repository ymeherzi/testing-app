package com.predictor.auth.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A device that already passed a code check and may skip the next one. */
@Entity
@Table(name = "trusted_devices")
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    private String label;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TrustedDevice() {
    }

    public TrustedDevice(Long userId, String tokenHash, String label, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.label = label;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }
}
