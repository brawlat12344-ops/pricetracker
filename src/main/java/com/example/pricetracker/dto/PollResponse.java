package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PollResponse(
        long snapshotId,
        UUID productId,
        BigDecimal price,
        String provider,
        boolean created,
        boolean alertCreated,
        Instant periodStart) {
}
