package com.example.pricetracker.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Короткий поисковый запрос для ссылки «Открыть». Магазины не находят товары, если в строку
 * пихать всю комплектацию (БП/ОС/корпус/охлаждение); достаточно модели CPU, GPU и объёма памяти.
 */
@Component
public class SearchQueryBuilder {

    public String build(String category, Map<String, String> specs, String title) {
        String prefix = switch (safe(category)) {
            case "laptop" -> "ноутбук";
            case "phone" -> "смартфон";
            case "headphones" -> "наушники";
            default -> "сборка ПК";
        };

        List<String> parts = new ArrayList<>();
        add(parts, specs.get("cpu"));
        add(parts, specs.get("gpu"));
        addNumber(parts, specs.get("ram_gb"), "ГБ");
        if (parts.size() < 3) {
            addNumber(parts, specs.get("storage_gb"), "ГБ");
        }
        if (parts.isEmpty() && title != null && !title.isBlank()) {
            Arrays.stream(title.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split("\\s+"))
                    .filter(word -> word.length() > 1)
                    .limit(5)
                    .forEach(parts::add);
        }
        if (parts.isEmpty()) {
            return prefix;
        }
        return (prefix + " " + String.join(" ", parts)).replaceAll("\\s+", " ").trim();
    }

    private void add(List<String> parts, String raw) {
        String value = clean(raw);
        if (!value.isEmpty() && !parts.contains(value)) {
            parts.add(value);
        }
    }

    private void addNumber(List<String> parts, String raw, String unit) {
        String value = clean(raw).replaceAll("[^0-9]", "");
        if (!value.isEmpty()) {
            parts.add(value + " " + unit);
        }
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
