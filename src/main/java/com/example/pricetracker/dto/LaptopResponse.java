package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.util.List;

/** Результат подбора ноутбуков: сравнение моделей, топ-1, альтернативы и отчёт эксперта. */
public record LaptopResponse(
        int budget,
        String purpose,
        String priority,
        String screen,
        String os,
        String requirements,
        String region,
        List<LaptopModel> models,
        LaptopModel topPick,
        String verdict,
        List<String> benchmarks,
        List<String> buyingWarnings,
        List<String> priceComparison,
        String forecast,
        boolean demoPrices,
        String pricedAt,
        List<String> warnings) {

    public record LaptopModel(
            String sku,
            String name,
            String config,
            String cpu,
            String gpu,
            String ram,
            String ssd,
            String display,
            BigDecimal price,
            String store,
            String url,
            String availability,
            String warranty,
            boolean demo,
            boolean promo,
            double score10,
            double cpuMark,
            double gpuMark,
            double batteryHours,
            String brightness,
            String coverage,
            double weightKg,
            String ports,
            String upgrade,
            String pros,
            String cons,
            String pitfalls,
            String forecast) {

        public String scoreLabel() {
            return Math.round(score10 * 10) / 10.0 + "/10";
        }
    }

    public BigDecimal budgetDelta() {
        if (topPick == null || topPick.price() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(budget).subtract(topPick.price());
    }
}
