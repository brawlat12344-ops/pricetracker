package com.example.pricetracker.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Простой in-memory кэш ответов с TTL. Не тянет внешнюю зависимость: для учебного демо
 * достаточно одного процесса и bounded-карты с вытеснением самой старой записи.
 */
@Component
public class ResponseCache {

    private record Cached(Object value, Instant expiresAt, String cacheName) {
    }

    private final ConcurrentHashMap<String, Cached> values = new ConcurrentHashMap<>();
    private final MeterRegistry registry;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    @Autowired
    public ResponseCache(MeterRegistry registry,
                         @Value("${pricetracker.cache.ttl-minutes:12}") long ttlMinutes,
                         @Value("${pricetracker.cache.max-entries:200}") int maxEntries) {
        this(registry, ttlMinutes, maxEntries, Clock.systemUTC());
    }

    public ResponseCache(MeterRegistry registry, long ttlMinutes, int maxEntries, Clock clock) {
        this.registry = registry;
        this.ttl = Duration.ofMinutes(Math.max(1, ttlMinutes));
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
        Gauge.builder("pt.cache.entries", values, Map::size)
                .description("Number of cached report responses")
                .register(registry);
    }

    public <T> T get(String cacheName, String key, Class<T> type, Supplier<T> loader) {
        Instant now = clock.instant();
        Cached cached = values.get(key);
        if (cached != null) {
            if (cached.expiresAt().isAfter(now)) {
                counter(cacheName, "hit");
                return type.cast(cached.value());
            }
            values.remove(key, cached);
        }
        counter(cacheName, "miss");
        T loaded = loader.get();
        if (loaded != null) {
            if (values.size() >= maxEntries) {
                evictOldest(now);
            }
            values.put(key, new Cached(loaded, now.plus(ttl), cacheName));
        }
        return loaded;
    }

    /** Только для тестов и диагностики: сколько записей сейчас в кэше. */
    public int size() {
        return values.size();
    }

    private void evictOldest(Instant now) {
        values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .findFirst()
                .ifPresent(entry -> values.remove(entry.getKey(), entry.getValue()));
        long expired = values.entrySet().stream()
                .filter(entry -> !entry.getValue().expiresAt().isAfter(now))
                .count();
        if (expired > 0) {
            values.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        }
    }

    private void counter(String cacheName, String result) {
        registry.counter("pt.cache.access", "cache", cacheName, "result", result).increment();
    }
}
