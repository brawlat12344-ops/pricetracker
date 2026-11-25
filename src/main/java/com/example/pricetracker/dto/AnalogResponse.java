package com.example.pricetracker.dto;

import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;

import java.util.List;
import java.util.UUID;

public record AnalogResponse(
        String sourceUrl,
        String category,
        String categoryLabel,
        String source,
        List<AttributeDef> attributes,
        List<CompareItem> items,
        UUID winnerId,
        String verdict,
        List<String> warnings) {
}
