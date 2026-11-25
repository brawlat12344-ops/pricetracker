package com.example.pricetracker.compare;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Описание категорий и весов характеристик. Один источник правды и для API, и для расчётов.
 */
@Component
public class CategoryCatalog {

    public record AttributeDef(
            String key,
            String label,
            String unit,
            double weight,
            boolean higherBetter,
            double reference,
            Map<String, Double> textScores,
            String icon) {

        public String weightLabel() {
            return Math.round(weight) + "%";
        }
    }

    public record CategoryDef(
            String key,
            String label,
            String description,
            List<AttributeDef> attributes) {
    }

    private static Map<String, Double> scores(Object... pairs) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i].toString().toLowerCase(), ((Number) pairs[i + 1]).doubleValue());
        }
        return Map.copyOf(result);
    }

    private static AttributeDef number(String key, String label, String unit, double weight,
                                       boolean higherBetter, double reference) {
        return number(key, label, unit, weight, higherBetter, reference, "");
    }

    private static AttributeDef number(String key, String label, String unit, double weight,
                                       boolean higherBetter, double reference, String icon) {
        return new AttributeDef(key, label, unit, weight, higherBetter, reference, Map.of(), icon);
    }

    private static AttributeDef text(String key, String label, double weight, Map<String, Double> textScores) {
        return text(key, label, weight, textScores, "");
    }

    private static AttributeDef text(String key, String label, double weight,
                                     Map<String, Double> textScores, String icon) {
        return new AttributeDef(key, label, "", weight, true, 0, textScores, icon);
    }

    private static final Map<String, CategoryDef> CATALOG = build();

    private static Map<String, CategoryDef> build() {
        Map<String, CategoryDef> map = new LinkedHashMap<>();

        map.put("laptop", new CategoryDef(
                "laptop", "Ноутбуки", "Производительность, экран, автономность и вес",
                List.of(
                        text("cpu", "Процессор", 25, scores(
                                "i9", 100, "i7", 85, "i5", 65, "i3", 45,
                                "ryzen 9", 95, "ryzen 7", 80, "ryzen 5", 60,
                                "m3", 90, "m2", 78, "m1", 70), "🧠"),
                        text("gpu", "Видеокарта", 20, scores(
                                "rtx 4090", 100, "rtx 4080", 90, "rtx 4070", 76,
                                "rtx 4060", 62, "rtx 3060", 46, "rx 7900", 85,
                                "arc", 52, "iris", 30, "integrated", 20, "m3", 70, "m2", 62), "🎮"),
                        number("ram_gb", "Оперативная память", "ГБ", 15, true, 64, "💾"),
                        number("storage_gb", "Накопитель", "ГБ", 10, true, 2048, "🗄️"),
                        text("screen", "Экран", 10, scores(
                                "4k", 95, "oled", 90, "144hz", 85, "120hz", 75,
                                "90hz", 60, "full hd", 50, "fhd", 50), "🖥️"),
                        number("battery_mah", "Батарея", "мА·ч", 10, true, 10000, "🔋"),
                        number("weight_kg", "Вес", "кг", 10, false, 2.5, "⚖️"))));

        map.put("phone", new CategoryDef(
                "phone", "Телефоны", "Камера, чип, память и автономность",
                List.of(
                        text("cpu", "Процессор", 20, scores(
                                "snapdragon 8", 92, "snapdragon 7", 70, "dimensity 9000", 85,
                                "apple a17", 100, "apple a16", 90, "exynos 2400", 80,
                                "helio", 55), "🧠"),
                        number("ram_gb", "Оперативная память", "ГБ", 15, true, 16, "💾"),
                        number("storage_gb", "Память", "ГБ", 10, true, 512, "🗄️"),
                        text("camera", "Камера", 25, scores(
                                "200mp", 98, "108mp", 82, "64mp", 68, "50mp", 60, "12mp", 45), "📷"),
                        number("battery_mah", "Батарея", "мА·ч", 15, true, 6000, "🔋"),
                        text("screen", "Экран", 10, scores(
                                "120hz", 90, "144hz", 95, "90hz", 70, "60hz", 50, "oled", 85), "🖥️"),
                        number("weight_kg", "Вес", "кг", 5, false, 0.25, "⚖️"))));

        map.put("headphones", new CategoryDef(
                "headphones", "Наушники", "Звук, шумоподавление, время работы и вес",
                List.of(
                        text("driver", "Излучатель", 20, scores(
                                "planar", 95, "dynamic", 65, "balanced armature", 70), "🎧"),
                        text("anc", "Шумоподавление", 25, scores(
                                "adaptive", 100, "yes", 90, "active", 85, "no", 30), "🔇"),
                        number("battery_h", "Время работы", "ч", 20, true, 60, "🔋"),
                        text("connectivity", "Подключение", 15, scores(
                                "bt 5.4", 100, "bt 5.3", 92, "bt 5.2", 82,
                                "bt 5.0", 65, "wired", 55), "📶"),
                        number("weight_kg", "Вес", "кг", 20, false, 0.4, "⚖️"))));

        map.put("pc", new CategoryDef(
                "pc", "Сборка ПК", "CPU, GPU, RAM, SSD, БП, охлаждение, ОС и корпус",
                List.of(
                        text("cpu", "CPU · Процессор", 22, scores(
                                "i9", 100, "i7", 85, "i5", 65, "i3", 45,
                                "ryzen 9", 95, "ryzen 7", 80, "ryzen 5", 60), "🧠"),
                        text("gpu", "GPU · Видеокарта", 22, scores(
                                "rtx 4090", 100, "rtx 4080", 90, "rtx 4070", 76,
                                "rtx 4060", 62, "rx 7900", 85, "rx 7800", 74,
                                "arc", 52), "🎮"),
                        number("ram_gb", "RAM · Память", "ГБ", 12, true, 64, "💾"),
                        number("storage_gb", "SSD · Накопитель", "ГБ", 10, true, 4096, "🗄️"),
                        number("psu_w", "БП · Питание", "Вт", 8, true, 1200, "⚡"),
                        text("cooling", "СО · Охлаждение", 8, scores(
                                "liquid", 95, "aio", 90, "water", 88,
                                "air", 60, "stock", 40, "боксов", 40), "❄️"),
                        text("os", "ОС · Система", 10, scores(
                                "windows 11 pro", 100, "windows 11", 90,
                                "windows 10 pro", 78, "windows 10", 70,
                                "linux", 55, "ubuntu", 55, "без ос", 20, "no os", 20), "🪟"),
                        text("case", "Корпус", 8, scores(
                                "full tower", 95, "midi tower", 85, "midi", 80,
                                "micro-atx", 70, "m-atx", 70, "atx", 60,
                                "mini-itx", 55, "mini", 55, "compact", 45), "🖥️"))));

        return Map.copyOf(map);
    }

    public List<CategoryDef> definitions() {
        return List.of(
                CATALOG.get("laptop"),
                CATALOG.get("phone"),
                CATALOG.get("headphones"),
                CATALOG.get("pc"));
    }

    public CategoryDef get(String key) {
        CategoryDef def = CATALOG.get(key == null ? "" : key.toLowerCase());
        if (def == null) {
            throw new IllegalArgumentException("Unknown category: " + key);
        }
        return def;
    }

    public boolean exists(String key) {
        return key != null && CATALOG.containsKey(key.toLowerCase());
    }
}
