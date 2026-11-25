package com.example.pricetracker.analog;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Стабильный демо-каталог сборок ПК. Источник не делает сетевых запросов:
 * карточки нужны, чтобы защита и демо работали даже без выхода в интернет.
 * Цены и характеристики заданы явно и не зависят от MOCK_BASE_PRICE.
 */
@Component
public class DemoCatalogSource implements AnalogSource {

    private static final String BASE = "https://demo.example.com/pc/";

    private static final List<Candidate> BUILDS = List.of(
            build("01-core-i5-rtx4060", "Core i5 + RTX 4060 · старт", "89900",
                    "Core i5-14600K", "RTX 4060", "16", "1024", "650", "stock"),
            build("02-core-i5-rtx4070", "Core i5 + RTX 4070 · баланс", "124500",
                    "Core i5-14600K", "RTX 4070", "32", "2048", "750", "air"),
            build("03-ryzen5-rx7800", "Ryzen 5 + RX 7800 XT", "137900",
                    "Ryzen 5 7600X", "RX 7800 XT", "32", "2048", "850", "AIO"),
            build("04-ryzen7-rtx4070", "Ryzen 7 + RTX 4070", "149900",
                    "Ryzen 7 7700X", "RTX 4070", "32", "2048", "750", "air"),
            build("05-core-i7-rtx4070", "Core i7 + RTX 4070 · pro", "169900",
                    "Core i7-14700K", "RTX 4070", "32", "2048", "850", "AIO"),
            build("06-ryzen9-rx7900", "Ryzen 9 + RX 7900 XTX", "219900",
                    "Ryzen 9 7900X", "RX 7900 XTX", "32", "4096", "1000", "liquid"),
            build("07-core-i7-rtx4080", "Core i7 + RTX 4080", "259900",
                    "Core i7-14700K", "RTX 4080", "64", "4096", "1000", "liquid"),
            build("08-ryzen5-rtx4060", "Ryzen 5 + RTX 4060 · бюджет", "92500",
                    "Ryzen 5 7600X", "RTX 4060", "16", "1024", "650", "stock"),
            build("09-core-i9-rtx4090", "Core i9 + RTX 4090 · флагман", "399900",
                    "Core i9-14900K", "RTX 4090", "64", "4096", "1200", "liquid"),
            build("10-ryzen7-rtx4080", "Ryzen 7 7800X3D + RTX 4080", "239900",
                    "Ryzen 7 7800X3D", "RTX 4080", "32", "2048", "850", "AIO"),
            build("11-core-i5-arc", "Core i5 + Arc A770", "79900",
                    "Core i5-14400", "Arc A770", "16", "1024", "650", "air"),
            build("12-ryzen9-rtx4090", "Ryzen 9 + RTX 4090 · проект", "429900",
                    "Ryzen 9 7950X", "RTX 4090", "64", "4096", "1200", "liquid"));

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public List<Candidate> find(String sourceUrl, String category, String title, int limit) {
        if (!"pc".equalsIgnoreCase(category)) {
            return List.of();
        }
        String normalized = normalize(sourceUrl);
        List<Candidate> result = new ArrayList<>();
        for (Candidate build : BUILDS) {
            if (!normalize(build.url()).equals(normalized)) {
                result.add(build);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static Candidate build(String slug, String title, String price,
                                   String cpu, String gpu, String ram, String storage,
                                   String psu, String cooling) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("cpu", cpu);
        specs.put("gpu", gpu);
        specs.put("ram_gb", ram);
        specs.put("storage_gb", storage);
        specs.put("psu_w", psu);
        specs.put("cooling", cooling);
        return new Candidate(BASE + slug, title, new BigDecimal(price), Map.copyOf(specs));
    }

    /** Приводим URL к виду без схемы и завершающего слеша — для дедупликации. */
    static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String value = url.trim().toLowerCase();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
