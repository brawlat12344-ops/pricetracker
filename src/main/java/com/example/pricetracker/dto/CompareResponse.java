package com.example.pricetracker.dto;

import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;

import java.util.List;
import java.util.UUID;

public record CompareResponse(
        UUID comparisonId,
        String category,
        String categoryLabel,
        List<AttributeDef> attributes,
        List<CompareItem> items,
        UUID winnerId,
        String verdict) {
}
