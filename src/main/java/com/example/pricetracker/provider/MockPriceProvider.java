package com.example.pricetracker.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Deterministic demo provider. Price = base + hash(url) % 100 + (bucket % 5) * 10.
 * With base=8990 and threshold=9500 an alert is guaranteed on the first tick.
 */
@Component
public class MockPriceProvider implements PriceProvider {

    private final BigDecimal base;
    private final long intervalMs;

    public MockPriceProvider(
            @Value("${pricetracker.mock-base-price:8990}") BigDecimal base,
            @Value("${pricetracker.poll-interval-ms:60000}") long intervalMs) {
        this.base = base;
        this.intervalMs = intervalMs;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public PriceResult fetch(String url) {
        long bucket = Instant.now().toEpochMilli() / Math.max(intervalMs, 1);
        int urlNoise = hash(url) % 100;
        BigDecimal tickNoise = BigDecimal.valueOf((bucket % 5) * 10);
        BigDecimal price = base.add(BigDecimal.valueOf(urlNoise)).add(tickNoise)
                .setScale(2, RoundingMode.HALF_UP);
        return PriceResult.of(price);
    }

    private int hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            long high = 0;
            for (int i = 0; i < 8; i++) {
                high = (high << 8) | (digest[i] & 0xffL);
            }
            return (int) (high & 0x7fffffff);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required by JVM", e);
        }
    }
}
