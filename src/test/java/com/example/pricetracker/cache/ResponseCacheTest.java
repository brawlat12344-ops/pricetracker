package com.example.pricetracker.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseCacheTest {

    @Test
    void cachesWithinTtlAndReloadsAfterExpiration() {
        MutableClock clock = new MutableClock();
        ResponseCache cache = new ResponseCache(new SimpleMeterRegistry(), 1, 10, clock);
        AtomicInteger calls = new AtomicInteger();

        assertEquals("report", cache.get("build", "key", String.class, () -> {
            calls.incrementAndGet();
            return "report";
        }));
        assertEquals("report", cache.get("build", "key", String.class, () -> {
            calls.incrementAndGet();
            return "report";
        }));
        assertEquals(1, calls.get());

        clock.advance(Duration.ofMinutes(2));
        assertEquals("report", cache.get("build", "key", String.class, () -> {
            calls.incrementAndGet();
            return "report";
        }));
        assertEquals(2, calls.get());
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
