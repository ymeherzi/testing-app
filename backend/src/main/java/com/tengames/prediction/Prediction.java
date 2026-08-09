package com.tengames.prediction;

import com.tengames.gameweek.GameweekFixture;
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
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gameweek_fixture_id")
    private GameweekFixture gameweekFixture;

    @Column(name = "home_goals", nullable = false)
    private int homeGoals;

    @Column(name = "away_goals", nullable = false)
    private int awayGoals;

    private Integer points;

    @Column(name = "scored_at")
    private Instant scoredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Prediction() {
    }

    public Prediction(Long userId, GameweekFixture gameweekFixture, int homeGoals, int awayGoals, Instant updatedAt) {
        this.userId = userId;
        this.gameweekFixture = gameweekFixture;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public GameweekFixture getGameweekFixture() {
        return gameweekFixture;
    }

    public int getHomeGoals() {
        return homeGoals;
    }

    public int getAwayGoals() {
        return awayGoals;
    }

    public void updateScoreline(int homeGoals, int awayGoals, Instant now) {
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.updatedAt = now;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points, Instant scoredAt) {
        this.points = points;
        this.scoredAt = scoredAt;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
