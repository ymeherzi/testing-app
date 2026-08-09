package com.tengames.catalog;

/** Mirrors football-data.org v4 match statuses. */
public enum MatchStatus {
    SCHEDULED,
    TIMED,
    IN_PLAY,
    PAUSED,
    FINISHED,
    SUSPENDED,
    POSTPONED,
    CANCELLED,
    AWARDED;

    public boolean isFinal() {
        return this == FINISHED || this == AWARDED;
    }

    /** Match no longer counts for predictions (design doc §2.3: fixture voided). */
    public boolean isVoided() {
        return this == POSTPONED || this == CANCELLED;
    }
}
