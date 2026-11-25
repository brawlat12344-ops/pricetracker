package com.example.pricetracker.store;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Строгая проверка «та же или лучшая комплектация».
 * Для CPU/GPU/экрана сравниваются баллы из CategoryCatalog, для чисел — значения.
 */
@Component
public class SameConfigMatcher {

    private final CategoryCatalog catalog;

    public SameConfigMatcher(CategoryCatalog catalog) {
        this.catalog = catalog;
    }

    public boolean matches(String category, Map<String, String> source, Map<String, String> candidate) {
        return reason(category, source, candidate) == null;
    }

    public String reason(String category, Map<String, String> source, Map<String, String> candidate) {
        for (AttributeDef attribute : catalog.get(category).attributes()) {
            String expected = source.get(attribute.key());
            String actual = candidate.get(attribute.key());
            if (expected == null || expected.isBlank()) {
                continue;
            }
            if (actual == null || actual.isBlank()) {
                return "нет параметра " + attribute.key();
            }
            if (!attribute.textScores().isEmpty()) {
                double sourceScore = score(attribute, expected);
                double actualScore = score(attribute, actual);
                if (actualScore < sourceScore) {
                    return attribute.key() + ": " + actual + " хуже, чем " + expected;
                }
            } else {
                Double sourceNumber = number(expected);
                Double actualNumber = number(actual);
                if (sourceNumber == null || actualNumber == null) {
                    return attribute.key() + ": не удалось сравнить числа";
                }
                boolean better = attribute.higherBetter()
                        ? actualNumber >= sourceNumber
                        : actualNumber <= sourceNumber;
                if (!better) {
                    return attribute.key() + ": " + actual + " хуже, чем " + expected;
                }
            }
        }
        return null;
    }

    private double score(AttributeDef attribute, String raw) {
        String value = raw.toLowerCase();
        return attribute.textScores().entrySet().stream()
                .filter(entry -> value.contains(entry.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(35);
    }

    private Double number(String raw) {
        try {
            String cleaned = raw.toLowerCase().replace(',', '.').replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) {
                return null;
            }
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
