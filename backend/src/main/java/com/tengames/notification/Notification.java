package com.tengames.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One email already sent to one player about one round.
 *
 * <p>Rows exist to be counted and to be found: the unique index on
 * (user, gameweek, kind) is what makes a second send impossible, whatever
 * happens to the batch that wrote the first.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Kind {
        /** The round is published and open for predictions. */
        ROUND_OPENED,
        /** The first kickoff is close and their card is still incomplete. */
        KICKOFF_REMINDER,
        /** The season is starting — announced once, by the admin, and never per round. */
        SEASON_LAUNCH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Null for an announcement that belongs to no particular round. */
    @Column(name = "gameweek_id")
    private Long gameweekId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(Long userId, Long gameweekId, Kind kind, Instant sentAt) {
        this.userId = userId;
        this.gameweekId = gameweekId;
        this.kind = kind;
        this.sentAt = sentAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getGameweekId() {
        return gameweekId;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
