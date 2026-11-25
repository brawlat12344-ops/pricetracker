package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String title,
        String url,
        String category,
        Map<String, String> attributes,
        BigDecimal alertThreshold,
        boolean active,
        Instant createdAt,
        BigDecimal latestPrice,
        long unreadAlerts) {
}
