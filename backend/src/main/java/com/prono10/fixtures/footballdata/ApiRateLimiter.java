package com.prono10.fixtures.footballdata;

import java.time.Clock;
import java.time.Instant;

/**
 * Minimal fixed-window limiter shared by all football-data.org calls.
 * Blocking is fine here: callers are scheduled jobs on virtual threads.
 */
public class ApiRateLimiter {

    private final int permitsPerMinute;
    private final Clock clock;

    private Instant windowStart;
    private int used;

    public ApiRateLimiter(int permitsPerMinute, Clock clock) {
        this.permitsPerMinute = permitsPerMinute;
        this.clock = clock;
        this.windowStart = clock.instant();
    }

    public synchronized void acquire() {
        Instant now = clock.instant();
        if (now.isAfter(windowStart.plusSeconds(60))) {
            windowStart = now;
            used = 0;
        }
        if (used < permitsPerMinute) {
            used++;
            return;
        }
        long waitMillis = Math.max(1, windowStart.plusSeconds(60).toEpochMilli() - now.toEpochMilli());
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for API rate limit", e);
        }
        windowStart = clock.instant();
        used = 1;
    }
}
