package com.example.pricetracker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavedComparison(
        UUID id,
        String name,
        String category,
        List<UUID> productIds,
        UUID winnerId,
        Instant createdAt) {
}
