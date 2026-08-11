package com.tengames.gameweek;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "gameweeks")
public class Gameweek {

    public enum Type {WEEKEND, MIDWEEK}

    public enum Status {DRAFT, PUBLISHED, SCORED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String season;

    @Column(name = "week_index", nullable = false)
    private int weekIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "counts_towards_table", nullable = false)
    private boolean countsTowardsTable = true;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    protected Gameweek() {
    }

    public Gameweek(String season, int weekIndex, Type type, Instant windowStart, Instant windowEnd) {
        this.season = season;
        this.weekIndex = weekIndex;
        this.type = type;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Long getId() {
        return id;
    }

    public String getSeason() {
        return season;
    }

    public int getWeekIndex() {
        return weekIndex;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /** False for a warm-up round: points are shown but never ranked. */
    public boolean isCountsTowardsTable() {
        return countsTowardsTable;
    }

    public void setCountsTowardsTable(boolean countsTowardsTable) {
        this.countsTowardsTable = countsTowardsTable;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindow(Instant windowStart, Instant windowEnd) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }
}
