package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CheaperItem(
        UUID id,
        String store,
        String title,
        String url,
        BigDecimal price,
        BigDecimal sourcePrice,
        Map<String, String> attributes,
        double score,
        double valueScore,
        List<String> missingAttributes) {

    /** Алиас для общего фрагмента таблицы сравнения. */
    public UUID productId() {
        return id;
    }

    public int discountPercent() {
        if (sourcePrice == null || sourcePrice.signum() <= 0) {
            return 0;
        }
        return sourcePrice.subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(sourcePrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public double roundedScore() {
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public double roundedValue() {
        return BigDecimal.valueOf(valueScore).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
