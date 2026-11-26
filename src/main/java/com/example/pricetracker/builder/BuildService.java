package com.example.pricetracker.builder;

import com.example.pricetracker.builder.PartCatalog.Part;
import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.BuildResponse.BuildPart;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Локальный конфигуратор: выбирает совместимые детали из каталога, считает оценку
 * и запрашивает цену на всю сборку одним поисковым запросом.
 */
@Service
public class BuildService {

    private final PartCatalog catalog;
    private final CategoryCatalog categories;
    private final PartPriceService prices;

    public BuildService(PartCatalog catalog, CategoryCatalog categories, PartPriceService prices) {
        this.catalog = catalog;
        this.categories = categories;
        this.prices = prices;
    }

    public BuildResponse build(BuildRequest request) {
        String category = request.categoryOrPc();
        String purpose = request.purposeOrBalanced();
        int budget = request.budgetOrDefault();
        List<String> types = catalog.types(category);
        Map<String, Double> weights = weights(category, purpose, types);

        Candidate chosen = choose(0, types, category, weights, budget,
                new ArrayList<>(), BigDecimal.ZERO, 0, 0);
        if (chosen == null || chosen.parts().isEmpty()) {
            throw new IllegalStateException("В каталоге нет подходящих комплектующих для этой темы");
        }

        String query = query(category, chosen.parts());
        BigDecimal fallback = catalog.baseTotal(category, chosen.parts());
        PartPriceService.Result priced = prices.price(query, fallback);
        BigDecimal total = priced.price().value();
        List<String> warnings = new ArrayList<>(priced.warnings());

        BigDecimal delta = BigDecimal.valueOf(budget).subtract(total);
        if (delta.signum() < 0) {
            warnings.add("Сборка превышает бюджет на " + money(delta.abs())
                    + ": оценка повышена за счёт более сильных комплектующих.");
        }

        double score = round1(chosen.score() / 10);
        String budgetText = delta.signum() == 0
                ? "без превышения бюджета"
                : (delta.signum() < 0 ? "+" : "−") + money(delta.abs()) + " ₽ к бюджету";
        String verdict = "Сборка: " + money(total) + " ₽ (" + budgetText + "). "
                + "Оценка " + score + "/10."
                + (priced.price().demo() ? " Демо-цена — проверьте в магазине." : "");

        return new BuildResponse(
                category,
                categories.get(category).label(),
                purposeLabel(purpose),
                budget,
                toParts(chosen.parts(), purpose),
                query,
                total,
                priced.price().store(),
                priced.price().url(),
                priced.price().demo(),
                score,
                verdict,
                warnings);
    }

    private record Candidate(List<Part> parts, BigDecimal total, double score, boolean over) {
    }

    private Candidate choose(int index, List<String> types, String category,
                             Map<String, Double> weights, int budget,
                             List<Part> chosen, BigDecimal total,
                             double weighted, double weightSum) {
        if (index == types.size()) {
            double score = weightSum == 0 ? 0 : weighted / weightSum;
            boolean over = total.compareTo(BigDecimal.valueOf(budget)) > 0;
            return new Candidate(List.copyOf(chosen), total, score, over);
        }

        String type = types.get(index);
        double weight = weights.getOrDefault(type, 1.0);
        Candidate best = null;
        for (Part part : catalog.parts(category)) {
            if (!part.type().equals(type) || !compatible(type, part, chosen)) {
                continue;
            }
            chosen.add(part);
            Candidate candidate = choose(index + 1, types, category, weights, budget, chosen,
                    total.add(part.basePrice()),
                    weighted + weight * part.score(),
                    weightSum + weight);
            chosen.remove(chosen.size() - 1);
            if (candidate != null && (best == null || better(candidate, best))) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean better(Candidate candidate, Candidate current) {
        if (candidate.over() != current.over()) {
            return !candidate.over();
        }
        if (Math.abs(candidate.score() - current.score()) > 0.0001) {
            return candidate.score() > current.score();
        }
        return candidate.total().compareTo(current.total()) < 0;
    }

    private boolean compatible(String type, Part part, List<Part> chosen) {
        Part cpu = find(chosen, "cpu");
        Part gpu = find(chosen, "gpu");

        if ("ram_gb".equals(type) && cpu != null) {
            String memory = cpu.traits().get("memory");
            return memory == null || memory.equals(part.traits().get("memory"));
        }
        if ("cooling".equals(type) && cpu != null) {
            String sockets = part.traits().get("sockets");
            return sockets != null && sockets.contains(cpu.traits().get("socket"));
        }
        if ("psu_w".equals(type)) {
            double required = number(cpu, "tdp") + number(gpu, "tdp") + 150;
            return number(part, "watts") >= required;
        }
        return true;
    }

    private Part find(List<Part> parts, String type) {
        return parts.stream().filter(part -> part.type().equals(type)).findFirst().orElse(null);
    }

    private double number(Part part, String key) {
        if (part == null) {
            return 0;
        }
        try {
            return Double.parseDouble(part.traits().getOrDefault(key, "0"));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<String, Double> weights(String category, String purpose, List<String> types) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (AttributeDef attribute : categories.get(category).attributes()) {
            result.put(attribute.key(), attribute.weight());
        }
        switch (purpose) {
            case "game" -> boost(result, types, "gpu", 20.0);
            case "work" -> {
                boost(result, types, "cpu", 15.0);
                boost(result, types, "ram_gb", 10.0);
            }
            case "study" -> {
                boost(result, types, "storage_gb", 5.0);
                boost(result, types, "battery_mah", 10.0);
            }
            default -> {
                // Универсальный профиль использует базовые веса категории.
            }
        }
        return result;
    }

    private void boost(Map<String, Double> weights, List<String> types, String key, double extra) {
        if (types.contains(key)) {
            weights.merge(key, extra, Double::sum);
        }
    }

    private String query(String category, List<Part> parts) {
        StringBuilder text = new StringBuilder("laptop".equals(category) ? "ноутбук " : "сборка ПК ");
        for (Part part : parts) {
            text.append(part.name()).append(' ').append(part.spec()).append(' ');
        }
        String query = text.toString().replaceAll("\\s+", " ").trim();
        return query.length() > 300 ? query.substring(0, 300) : query;
    }

    private List<BuildPart> toParts(List<Part> parts, String purpose) {
        return parts.stream()
                .map(part -> new BuildPart(
                        part.type(), part.label(), part.name(), part.spec(),
                        Math.round(part.score()) / 10.0,
                        reason(part.type(), purpose)))
                .toList();
    }

    private String reason(String type, String purpose) {
        String task = purposeLabel(purpose).toLowerCase();
        return switch (type) {
            case "cpu" -> "процессор под задачу: " + task;
            case "gpu" -> "графика с учётом задачи: " + task;
            case "ram_gb" -> "достаточный запас памяти";
            case "storage_gb" -> "объём накопителя для файлов и программ";
            case "psu_w" -> "запас мощности с учётом CPU и GPU";
            case "cooling" -> "совместим с выбранным сокетом";
            case "os" -> "система из каталога";
            case "case" -> "форм-фактор под выбранную сборку";
            case "screen" -> "экран для " + task;
            case "battery_mah" -> "автономность для " + task;
            case "weight_kg" -> "вес устройства для " + task;
            default -> "выбрано локальным конфигуратором";
        };
    }

    private String purposeLabel(String purpose) {
        return switch (purpose) {
            case "game" -> "Игры";
            case "work" -> "Работа";
            case "study" -> "Учёба";
            default -> "Универсально";
        };
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String money(BigDecimal value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', '\u00A0');
    }
}
