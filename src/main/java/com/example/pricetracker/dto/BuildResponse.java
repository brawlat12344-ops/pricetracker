package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.util.List;

/** Результат локального конфигуратора: подобранная сборка и цена на неё целиком. */
public record BuildResponse(
        String category,
        String categoryLabel,
        String purpose,
        int budget,
        List<BuildPart> parts,
        String query,
        BigDecimal totalPrice,
        String store,
        String priceUrl,
        boolean demoPrice,
        double score,
        String verdict,
        List<String> warnings) {

    public record BuildPart(String type, String label, String name, String spec,
                            double score10, String reason) {
    }

    public BigDecimal budgetDelta() {
        return BigDecimal.valueOf(budget).subtract(totalPrice);
    }

    public boolean overBudget() {
        return budgetDelta().signum() < 0;
    }

    public String scoreLabel() {
        return Math.round(score * 10) / 10.0 + "/10";
    }
}
