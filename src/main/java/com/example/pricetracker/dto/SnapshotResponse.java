package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotResponse(
        long id,
        BigDecimal price,
        String currency,
        Instant capturedAt,
        String provider) {
}
