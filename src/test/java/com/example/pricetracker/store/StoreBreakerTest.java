package com.example.pricetracker.store;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreBreakerTest {

    @Test
    void opensAfterThresholdAndAllowsProbeAfterCooldown() {
        MutableClock clock = new MutableClock();
        StoreBreaker breaker = new StoreBreaker(new SimpleMeterRegistry(), 2, 10, clock);

        assertTrue(breaker.allow("dns"));
        breaker.failure("dns", "blocked");
        breaker.failure("dns", "blocked");
        assertFalse(breaker.allow("dns"));

        clock.advance(Duration.ofSeconds(11));
        assertTrue(breaker.allow("dns"));
        breaker.success("dns");
        assertTrue(breaker.allow("dns"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
