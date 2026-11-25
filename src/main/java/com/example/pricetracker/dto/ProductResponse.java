package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String title,
        String url,
        BigDecimal alertThreshold,
        boolean active,
        Instant createdAt,
        BigDecimal latestPrice,
        long unreadAlerts) {
}
