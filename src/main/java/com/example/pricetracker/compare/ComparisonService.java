package com.example.pricetracker.compare;

import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;
import com.example.pricetracker.compare.CategoryCatalog.CategoryDef;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Единая формула скоринга для поиска дешевле: баллы за комплектацию,
 * делённые на цену («баллов за 1000 ₽»). Ничего не пишет в БД.
 */
@Service
public class ComparisonService {

    private final CategoryCatalog catalog;

    public ComparisonService(CategoryCatalog catalog) {
        this.catalog = catalog;
    }

    public record Evaluation(
            double score,
            double valueScore,
            Map<String, Double> attributeScores,
            List<String> missingAttributes) {
    }

    public Evaluation evaluate(String category, Map<String, String> attributes, BigDecimal price) {
        CategoryDef definition = catalog.get(category);
        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        double total = 0;

        for (AttributeDef attribute : definition.attributes()) {
            double score = attributeScore(attribute, attributes.get(attribute.key()));
            scores.put(attribute.key(), score);
            if (score <= 0) {
                missing.add(attribute.key());
            }
            total += attribute.weight() / 100.0 * score;
        }

        double value = price != null && price.signum() > 0
                ? total / price.doubleValue() * 1000
                : 0;
        return new Evaluation(total, value, scores, missing);
    }

    private double attributeScore(AttributeDef def, String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String value = raw.toLowerCase();

        if (!def.textScores().isEmpty()) {
            return def.textScores().entrySet().stream()
                    .filter(entry -> value.contains(entry.getKey()))
                    .mapToDouble(Map.Entry::getValue)
                    .max()
                    .orElse(35);
        }

        try {
            String cleaned = value.replace(',', '.').replaceAll("[^0-9.]", "");
            double number = Double.parseDouble(cleaned);
            if (number <= 0) {
                return 0;
            }
            double score = def.higherBetter()
                    ? number / def.reference() * 100
                    : def.reference() / number * 100;
            return Math.min(100, score);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
