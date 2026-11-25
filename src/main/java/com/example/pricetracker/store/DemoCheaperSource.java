package com.example.pricetracker.store;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Демо-фолбэк: варианты с той же или лучшей комплектацией и ценой ниже исходной.
 * Цены сгенерированы явно и помечаются как демо в предупреждениях ответа.
 */
@Component
public class DemoCheaperSource {

    /**
     * url не задаётся намеренно: ссылку строит CheapSearchService через StoreRegistry,
     * чтобы клик открывал реальный поиск магазина, а не фейковую демо-страницу.
     */
    public record Offer(String storeKey, String store, String title,
                        BigDecimal price, Map<String, String> specs) {
    }

    private static final List<String> STORE_KEYS = List.of(
            "dns", "mvideo", "citilink", "ozon", "wildberries");

    private static final List<String> STORES = List.of(
            "DNS-Shop", "М.Видео", "Ситилинк", "Ozon", "Wildberries");

    private static final List<String> DISCOUNTS = List.of("5", "9", "13", "17", "21", "25", "29");

    private final SameConfigMatcher matcher;

    public DemoCheaperSource(SameConfigMatcher matcher) {
        this.matcher = matcher;
    }

    public List<Offer> offers(String category, Map<String, String> specs,
                              BigDecimal sourcePrice, int limit) {
        List<Offer> result = new ArrayList<>();
        List<Map<String, String>> variants = variants(specs);

        for (int i = 0; i < variants.size() && result.size() < limit; i++) {
            Map<String, String> variant = variants.get(i);
            if (!matcher.matches(category, specs, variant) || sourcePrice.signum() <= 0) {
                continue;
            }
            int discount = Integer.parseInt(DISCOUNTS.get(i % DISCOUNTS.size()));
            BigDecimal price = sourcePrice.multiply(BigDecimal.valueOf(100 - discount))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.TEN)
                    .max(BigDecimal.valueOf(1000));
            int storeIndex = i % STORE_KEYS.size();
            result.add(new Offer(STORE_KEYS.get(storeIndex), STORES.get(storeIndex),
                    demoTitle(variant), price, variant));
        }
        return result;
    }

    private List<Map<String, String>> variants(Map<String, String> base) {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(copy(base));

        Map<String, String> ram = copy(base);
        ram.put("ram_gb", String.valueOf(Math.min(128, intValue(ram.get("ram_gb"), 16) * 2)));
        result.add(ram);

        Map<String, String> storage = copy(base);
        storage.put("storage_gb", String.valueOf(Math.min(8192, intValue(storage.get("storage_gb"), 1024) * 2)));
        result.add(storage);

        Map<String, String> cooling = copy(base);
        cooling.put("cooling", "AIO");
        result.add(cooling);

        Map<String, String> os = copy(base);
        os.put("os", upgradeOs(os.get("os")));
        result.add(os);

        Map<String, String> computerCase = copy(base);
        computerCase.put("case", upgradeCase(computerCase.get("case")));
        result.add(computerCase);

        Map<String, String> gpu = copy(base);
        gpu.put("gpu", upgradeGpu(gpu.get("gpu")));
        result.add(gpu);

        Map<String, String> cpu = copy(base);
        cpu.put("cpu", upgradeCpu(cpu.get("cpu")));
        result.add(cpu);

        Map<String, String> combo = copy(ram);
        combo.put("storage_gb", storage.get("storage_gb"));
        result.add(combo);

        Map<String, String> top = copy(cpu);
        top.put("gpu", gpu.get("gpu"));
        top.put("cooling", "liquid");
        result.add(top);

        Map<String, String> psu = copy(base);
        psu.put("psu_w", upgradePsu(psu.get("psu_w")));
        result.add(psu);

        Map<String, String> full = copy(combo);
        full.put("psu_w", upgradePsu(full.get("psu_w")));
        full.put("cooling", "liquid");
        result.add(full);
        return result;
    }

    private Map<String, String> copy(Map<String, String> source) {
        return new LinkedHashMap<>(source);
    }

    private String demoTitle(Map<String, String> specs) {
        String title = String.join(" / ", specs.getOrDefault("cpu", "ПК"),
                specs.getOrDefault("gpu", "без дискретной видеокарты"),
                specs.getOrDefault("ram_gb", "?") + " ГБ RAM",
                specs.getOrDefault("storage_gb", "?") + " ГБ SSD");
        if (specs.containsKey("psu_w")) {
            title += " / БП " + specs.get("psu_w") + " Вт";
        }
        if (specs.containsKey("case")) {
            title += " / корпус " + specs.get("case");
        }
        return title;
    }

    private int intValue(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private String upgradeGpu(String raw) {
        String value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("4060")) return "RTX 4070";
        if (value.contains("4070")) return "RTX 4080";
        if (value.contains("4080")) return "RTX 4090";
        if (value.contains("7800")) return "RX 7900 XTX";
        if (value.contains("arc")) return "RTX 4060";
        return raw == null ? "RTX 4070" : raw;
    }

    private String upgradeOs(String raw) {
        String value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("без") || value.contains("no os") || value.contains("linux")
                || value.contains("ubuntu") || value.contains("windows 10")) {
            return "Windows 11";
        }
        if (value.contains("windows 11") && !value.contains("pro")) {
            return "Windows 11 Pro";
        }
        return raw == null ? "Windows 11 Pro" : raw;
    }

    private String upgradeCase(String raw) {
        String value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("compact") || value.contains("mini")) return "micro-atx";
        if (value.contains("m-atx") || value.contains("micro")) return "midi tower";
        if (value.contains("midi") || value.contains("atx")) return "full tower";
        return raw == null ? "midi tower" : raw;
    }

    private String upgradePsu(String raw) {
        return String.valueOf(Math.min(1600, intValue(raw, 650) + 300));
    }

    private String upgradeCpu(String raw) {
        String value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("i5")) return "Core i7-14700K";
        if (value.contains("i7")) return "Core i9-14900K";
        if (value.contains("ryzen 5")) return "Ryzen 7 7700X";
        if (value.contains("ryzen 7")) return "Ryzen 9 7900X";
        return raw == null ? "Core i7-14700K" : raw;
    }
}
