package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        long id,
        UUID productId,
        String title,
        BigDecimal price,
        BigDecimal threshold,
        boolean read,
        Instant createdAt) {
}
