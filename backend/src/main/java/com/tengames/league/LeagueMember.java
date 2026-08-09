package com.tengames.league;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "league_members")
public class LeagueMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id")
    private League league;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Gameweek that was current/next at join time; NULL = count everything. */
    @Column(name = "join_gameweek_id")
    private Long joinGameweekId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected LeagueMember() {
    }

    public LeagueMember(League league, Long userId, Long joinGameweekId, Instant joinedAt) {
        this.league = league;
        this.userId = userId;
        this.joinGameweekId = joinGameweekId;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public League getLeague() {
        return league;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getJoinGameweekId() {
        return joinGameweekId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
