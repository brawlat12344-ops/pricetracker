package com.example.pricetracker.builder;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Локальный каталог комплектующих для конфигуратора.
 * Это не внешний LLM: здесь заданы совместимость, базовая цена и оценка детали.
 */
@Component
public class PartCatalog {

    public record Part(String type, String label, String name, String spec,
                       BigDecimal basePrice, double score, Map<String, String> traits) {
    }

    private static final Map<String, List<Part>> CATALOG = build();

    public List<Part> parts(String category) {
        return CATALOG.getOrDefault(norm(category), List.of());
    }

    public List<String> types(String category) {
        List<String> result = new ArrayList<>();
        for (Part part : parts(category)) {
            if (!result.contains(part.type())) {
                result.add(part.type());
            }
        }
        return result;
    }

    public BigDecimal baseTotal(String category, List<Part> parts) {
        BigDecimal sum = parts.stream()
                .map(Part::basePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if ("laptop".equals(norm(category)) && sum.signum() == 0) {
            return BigDecimal.valueOf(85000);
        }
        return sum;
    }

    private static String norm(String category) {
        if ("laptop".equalsIgnoreCase(category)) {
            return "laptop";
        }
        return "pc";
    }

    private static Part part(String type, String label, String name, String spec,
                             double price, double score, String... traits) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < traits.length; i += 2) {
            map.put(traits[i], traits[i + 1]);
        }
        return new Part(type, label, name, spec, BigDecimal.valueOf(price), score, Map.copyOf(map));
    }

    private static Map<String, List<Part>> build() {
        Map<String, List<Part>> map = new LinkedHashMap<>();

        map.put("pc", List.of(
                part("cpu", "CPU · Процессор", "Core i3-14100F", "LGA1700 · DDR4 · 60 Вт",
                        12000, 45, "socket", "LGA1700", "memory", "DDR4", "tdp", "60"),
                part("cpu", "CPU · Процессор", "Core i5-14600K", "LGA1700 · DDR5 · 125 Вт",
                        25000, 70, "socket", "LGA1700", "memory", "DDR5", "tdp", "125"),
                part("cpu", "CPU · Процессор", "Ryzen 5 7600", "AM5 · DDR5 · 65 Вт",
                        21000, 74, "socket", "AM5", "memory", "DDR5", "tdp", "65"),
                part("cpu", "CPU · Процессор", "Ryzen 7 7800X3D", "AM5 · DDR5 · 120 Вт",
                        38000, 95, "socket", "AM5", "memory", "DDR5", "tdp", "120"),
                part("cpu", "CPU · Процессор", "Core i7-14700K", "LGA1700 · DDR5 · 125 Вт",
                        42000, 88, "socket", "LGA1700", "memory", "DDR5", "tdp", "125"),

                part("gpu", "GPU · Видеокарта", "RTX 4060 8GB", "8 ГБ · 115 Вт",
                        32000, 62, "tdp", "115"),
                part("gpu", "GPU · Видеокарта", "RX 7800 XT 16GB", "16 ГБ · 263 Вт",
                        52000, 78, "tdp", "263"),
                part("gpu", "GPU · Видеокарта", "RTX 4070 12GB", "12 ГБ · 200 Вт",
                        58000, 82, "tdp", "200"),
                part("gpu", "GPU · Видеокарта", "RTX 4080 16GB", "16 ГБ · 320 Вт",
                        110000, 95, "tdp", "320"),

                part("ram_gb", "RAM · Память", "16 ГБ DDR4-3200", "2×8 ГБ · DDR4",
                        5000, 55, "memory", "DDR4"),
                part("ram_gb", "RAM · Память", "32 ГБ DDR5-5600", "2×16 ГБ · DDR5",
                        11000, 85, "memory", "DDR5"),
                part("ram_gb", "RAM · Память", "64 ГБ DDR5-6000", "2×32 ГБ · DDR5",
                        22000, 100, "memory", "DDR5"),

                part("storage_gb", "SSD · Накопитель", "1 ТБ NVMe", "PCIe 4.0",
                        8000, 55),
                part("storage_gb", "SSD · Накопитель", "2 ТБ NVMe", "PCIe 4.0",
                        14000, 80),
                part("storage_gb", "SSD · Накопитель", "4 ТБ NVMe", "PCIe 4.0",
                        28000, 100),

                part("psu_w", "БП · Питание", "БП 550 Вт", "80+ Bronze",
                        6000, 40, "watts", "550"),
                part("psu_w", "БП · Питание", "БП 750 Вт", "80+ Gold",
                        10000, 70, "watts", "750"),
                part("psu_w", "БП · Питание", "БП 1000 Вт", "80+ Platinum",
                        16000, 95, "watts", "1000"),

                part("cooling", "СО · Охлаждение", "Box cooler", "Воздушный · LGA1700/AM5",
                        1500, 35, "sockets", "LGA1700,AM5"),
                part("cooling", "СО · Охлаждение", "Air tower", "Башенный · LGA1700/AM5",
                        5000, 65, "sockets", "LGA1700,AM5"),
                part("cooling", "СО · Охлаждение", "AIO 240", "Жидкостный · LGA1700/AM5",
                        12000, 90, "sockets", "LGA1700,AM5"),

                part("os", "ОС · Система", "Без ОС", "установка не требуется",
                        0, 20),
                part("os", "ОС · Система", "Ubuntu 24.04", "Linux",
                        0, 55),
                part("os", "ОС · Система", "Windows 11 Home", "лицензия",
                        12000, 85),
                part("os", "ОС · Система", "Windows 11 Pro", "лицензия",
                        17000, 100),

                part("case", "Корпус", "micro-ATX", "компактный",
                        5000, 60),
                part("case", "Корпус", "Midi tower", "средний",
                        9000, 85),
                part("case", "Корпус", "Full tower", "полноразмерный",
                        15000, 95)));

        map.put("laptop", List.of(
                part("cpu", "CPU · Процессор", "Core i5-1340P", "12 ядер · DDR5",
                        0, 62, "memory", "DDR5"),
                part("cpu", "CPU · Процессор", "Ryzen 7 8845HS", "8 ядер · DDR5",
                        0, 82, "memory", "DDR5"),
                part("cpu", "CPU · Процессор", "Core i7-13700H", "14 ядер · DDR5",
                        0, 88, "memory", "DDR5"),

                part("gpu", "GPU · Видеокарта", "Intel Iris Xe", "встроенная",
                        0, 25),
                part("gpu", "GPU · Видеокарта", "RTX 4050 6GB", "6 ГБ",
                        0, 60),
                part("gpu", "GPU · Видеокарта", "RTX 4070 8GB", "8 ГБ",
                        0, 88),

                part("ram_gb", "RAM · Память", "16 ГБ DDR5", "2×8 ГБ",
                        0, 65, "memory", "DDR5"),
                part("ram_gb", "RAM · Память", "32 ГБ DDR5", "2×16 ГБ",
                        0, 90, "memory", "DDR5"),

                part("storage_gb", "SSD · Накопитель", "512 ГБ NVMe", "PCIe 4.0",
                        0, 55),
                part("storage_gb", "SSD · Накопитель", "1 ТБ NVMe", "PCIe 4.0",
                        0, 85),
                part("storage_gb", "SSD · Накопитель", "2 ТБ NVMe", "PCIe 4.0",
                        0, 100),

                part("screen", "Экран", "15.6\" IPS 60Hz", "Full HD",
                        0, 50),
                part("screen", "Экран", "15.6\" IPS 120Hz", "Full HD",
                        0, 78),
                part("screen", "Экран", "16\" OLED 120Hz", "2.8K",
                        0, 100),

                part("battery_mah", "Батарея", "5000 мА·ч", "≈ 6 ч",
                        0, 55),
                part("battery_mah", "Батарея", "7000 мА·ч", "≈ 9 ч",
                        0, 85),
                part("battery_mah", "Батарея", "9000 мА·ч", "≈ 12 ч",
                        0, 100),

                part("weight_kg", "Вес", "2.1 кг", "портативный",
                        0, 70),
                part("weight_kg", "Вес", "1.7 кг", "лёгкий",
                        0, 88),
                part("weight_kg", "Вес", "1.3 кг", "ультрапортативный",
                        0, 100)));

        return Map.copyOf(map);
    }
}
