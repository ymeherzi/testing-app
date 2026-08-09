package com.tengames.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A sliding window of recent hits per key, held in memory.
 *
 * <p>Deliberately not distributed: one instance serves the whole app, and a
 * limiter that occasionally forgets after a restart is far better than none.
 * Swap in Redis the day there are several instances.
 */
@Component
public class RateLimiter {

    /** Beyond this many distinct keys we stop tracking new ones rather than grow without bound. */
    private static final int MAX_KEYS = 100_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records one hit for {@code key} and refuses it when the window is full.
     *
     * @param message what the caller is told; it reaches the UI, so write it for a human
     * @throws ResponseStatusException 429 when the limit is already reached
     */
    public void check(String key, int limit, Duration window, String message) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        if (hits.size() >= MAX_KEYS) {
            prune(cutoff);
        }
        Deque<Instant> recent = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.pollFirst();
            }
            if (recent.size() >= limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
            }
            recent.addLast(now);
        }
    }

    /** Drops keys whose hits have all aged out; called only when the map grows large. */
    private void prune(Instant cutoff) {
        hits.entrySet().removeIf(entry -> {
            Deque<Instant> recent = entry.getValue();
            synchronized (recent) {
                return recent.isEmpty() || recent.peekLast().isBefore(cutoff);
            }
        });
    }
}
