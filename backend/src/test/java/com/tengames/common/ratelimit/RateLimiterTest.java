package com.tengames.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The limiter has to hold two lines at once: stop abuse, and never stand in
 * the way of someone who simply mistyped their code twice.
 */
class RateLimiterTest {

    static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-08-10T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final Duration WINDOW = Duration.ofHours(1);

    private final MovableClock clock = new MovableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    private void hit(String key) {
        limiter.check(key, 3, WINDOW, "slow down");
    }

    @Test
    void allowsUpToTheLimitThenRefuses() {
        hit("a");
        hit("a");
        hit("a");

        assertThatThrownBy(() -> hit("a"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("slow down");
    }

    @Test
    void countsEachKeySeparately() {
        hit("a");
        hit("a");
        hit("a");

        // one address hitting its limit must not lock out everyone else
        assertThatCode(() -> hit("b")).doesNotThrowAnyException();
    }

    @Test
    void forgivesOnceTheWindowHasPassed() {
        hit("a");
        hit("a");
        hit("a");

        clock.advance(WINDOW.plusSeconds(1));

        assertThatCode(() -> hit("a")).doesNotThrowAnyException();
    }

    @Test
    void slidesRatherThanResettingInBlocks() {
        hit("a");
        clock.advance(Duration.ofMinutes(50));
        hit("a");
        hit("a");

        // the first hit ages out here, freeing exactly one slot
        clock.advance(Duration.ofMinutes(11));
        assertThatCode(() -> hit("a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> hit("a")).isInstanceOf(ResponseStatusException.class);
    }
}
