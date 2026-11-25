package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CompareItem(
        UUID productId,
        String title,
        String url,
        BigDecimal price,
        double score,
        double valueScore,
        Map<String, String> attributes,
        Map<String, Double> attributeScores,
        List<String> missingAttributes) {

    public double roundedScore() {
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public double roundedValue() {
        return BigDecimal.valueOf(valueScore).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
