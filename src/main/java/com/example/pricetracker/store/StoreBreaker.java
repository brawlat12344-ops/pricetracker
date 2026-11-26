package com.example.pricetracker.store;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Лёгкий circuit breaker на один магазин. После нескольких подряд блокировок/сетевых ошибок
 * цепь размыкается и запросы к источнику пропускаются; через interval делается один пробный
 * запрос. Метрики доступны через /actuator/metrics.
 */
@Component
public class StoreBreaker {

    private static final class State {
        private volatile int failures;
        private volatile Instant openUntil;
        private volatile boolean halfOpen;

        private boolean open() {
            return openUntil != null;
        }
    }

    private final MeterRegistry registry;
    private final int threshold;
    private final Duration cooldown;
    private final Clock clock;
    private final ConcurrentMap<String, State> states = new ConcurrentHashMap<>();

    @Autowired
    public StoreBreaker(MeterRegistry registry,
                        @Value("${pricetracker.breaker.failure-threshold:3}") int threshold,
                        @Value("${pricetracker.breaker.cooldown-seconds:60}") long cooldownSeconds) {
        this(registry, threshold, cooldownSeconds, Clock.systemUTC());
    }

    public StoreBreaker(MeterRegistry registry, int threshold, long cooldownSeconds, Clock clock) {
        this.registry = registry;
        this.threshold = Math.max(1, threshold);
        this.cooldown = Duration.ofSeconds(Math.max(1, cooldownSeconds));
        this.clock = clock;
    }

    /** Можно ли делать запрос. Если цепь разомкнута — возвращает false и считает skip. */
    public synchronized boolean allow(String store) {
        State state = state(store);
        Instant now = clock.instant();
        if (state.openUntil == null) {
            return true;
        }
        if (!now.isBefore(state.openUntil)) {
            state.halfOpen = true;
            event(store, "half-open");
            return true;
        }
        outcome(store, "skipped");
        return false;
    }

    public synchronized void success(String store) {
        State state = state(store);
        if (state.open()) {
            state.openUntil = null;
            state.failures = 0;
            state.halfOpen = false;
            event(store, "closed");
        }
        outcome(store, "success");
    }

    /** outcome: blocked (401/403/429/bot-check), error (network/timeout), empty (SPA без карточек). */
    public synchronized void failure(String store, String outcome) {
        State state = state(store);
        outcome(store, outcome);
        if (state.halfOpen) {
            state.openUntil = clock.instant().plus(cooldown);
            state.halfOpen = false;
            state.failures = threshold;
            event(store, "open");
            return;
        }
        if ("empty".equals(outcome)) {
            return;
        }
        state.failures++;
        if (state.failures >= threshold && state.openUntil == null) {
            state.openUntil = clock.instant().plus(cooldown);
            event(store, "open");
        }
    }

    public void latency(String store, long startedNano) {
        registry.timer("pt.store.latency", "store", store)
                .record(Duration.ofNanos(System.nanoTime() - startedNano));
    }

    public synchronized Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        Instant now = clock.instant();
        states.forEach((store, state) -> result.put(store,
                state.openUntil == null
                        ? "closed (failures=" + state.failures + ")"
                        : (now.isBefore(state.openUntil) ? "open" : "half-open")
                        + " until " + state.openUntil));
        return result;
    }

    private State state(String store) {
        return states.computeIfAbsent(store, key -> {
            State fresh = new State();
            Gauge.builder("pt.store.breaker.open", fresh, state -> state.open() ? 1.0 : 0.0)
                    .tag("store", key)
                    .description("1 when the store circuit is open")
                    .register(registry);
            return fresh;
        });
    }

    private void outcome(String store, String outcome) {
        Counter.builder("pt.store.requests")
                .tag("store", store)
                .tag("outcome", outcome)
                .description("Store search attempts by outcome")
                .register(registry)
                .increment();
    }

    private void event(String store, String event) {
        registry.counter("pt.store.circuit", "store", store, "event", event).increment();
    }
}
