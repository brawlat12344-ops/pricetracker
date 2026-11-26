package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.util.List;

/** Результат экспертного конфигуратора: детали, предложения по каждой детали и полный отчёт. */
public record BuildResponse(
        String category,
        String categoryLabel,
        String purpose,
        String priority,
        String wishes,
        String region,
        int budget,
        List<BuildPart> parts,
        String query,
        BigDecimal totalPrice,
        String store,
        String priceUrl,
        boolean demoPrice,
        double score,
        String verdict,
        List<String> warnings,
        List<String> rationale,
        List<String> weightExplanation,
        List<String> assemblySteps,
        List<String> bottlenecks,
        BuildVariant alternative,
        String pricedAt) {

    public record ComponentOffer(String store, BigDecimal price, String url,
                                 String availability, boolean demo, boolean promo, String note) {
    }

    public record BuildPart(String type, String label, String name, String spec,
                            double score10, BigDecimal price, String reason,
                            String compatibility, List<ComponentOffer> offers) {
    }

    public record BuildVariant(String title, BigDecimal budget, BigDecimal totalPrice,
                               double score, List<BuildPart> parts, String summary,
                               boolean estimated) {
        public String scoreLabel() {
            return Math.round(score * 10) / 10.0 + "/10";
        }
    }

    public BigDecimal budgetDelta() {
        return BigDecimal.valueOf(budget).subtract(totalPrice);
    }

    /** По промпту нельзя превышать бюджет более чем на 3%. */
    public boolean overBudget() {
        BigDecimal allowed = BigDecimal.valueOf(budget).multiply(BigDecimal.valueOf(1.03));
        return totalPrice.compareTo(allowed) > 0;
    }

    public String scoreLabel() {
        return Math.round(score * 10) / 10.0 + "/10";
    }

    public long realOffersCount() {
        return parts.stream()
                .flatMap(part -> part.offers().stream())
                .filter(offer -> !offer.demo())
                .count();
    }

    public long demoOffersCount() {
        return parts.stream()
                .flatMap(part -> part.offers().stream())
                .filter(ComponentOffer::demo)
                .count();
    }
}
