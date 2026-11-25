package com.example.pricetracker.dto;

import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CheaperResponse(
        String sourceUrl,
        String store,
        String category,
        String categoryLabel,
        String sourceTitle,
        BigDecimal sourcePrice,
        Map<String, String> sourceSpecs,
        List<AttributeDef> attributes,
        List<CheaperItem> items,
        UUID winnerId,
        String verdict,
        List<String> warnings,
        int foundOffers,
        int rejectedOffers) {
}
