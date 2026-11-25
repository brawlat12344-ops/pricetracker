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
        Map<String, Double> attributeScores,
        double score,
        double valueScore,
        List<String> missingAttributes,
        boolean demo) {

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
        return scale(score);
    }

    public double roundedValue() {
        return scale(valueScore);
    }

    /** Ширина шкалы итоговой оценки: 0–10 превращается в 0–100%. */
    public long scorePercent() {
        return Math.round(Math.max(0, Math.min(10, score)) * 10);
    }

    /** Ширина шкалы цены/качества: 0–10 превращается в 0–100%. */
    public long valuePercent() {
        return Math.round(Math.max(0, Math.min(10, valueScore)) * 10);
    }

    /** Значение для сортировки без ограничения шкалой 0–10. */
    public double sortValue() {
        if (price == null || sourcePrice == null || price.signum() <= 0) {
            return 0;
        }
        return score * sourcePrice.doubleValue() / price.doubleValue();
    }

    private double scale(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Процент баллов за отдельный параметр — используется для шкал в таблице. */
    public long attributePercent(String key) {
        Double value = attributeScores == null ? null : attributeScores.get(key);
        return value == null ? 0 : Math.round(Math.max(0, Math.min(100, value)));
    }
}
