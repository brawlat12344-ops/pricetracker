package com.example.pricetracker.builder;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Локальный каталог комплектующих. Здесь заданы совместимость, габариты, TDP,
 * базовая цена и оценка детали — без внешней языковой модели.
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
        return "laptop".equalsIgnoreCase(category) ? "laptop" : "pc";
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
                        25000, 72, "socket", "LGA1700", "memory", "DDR5", "tdp", "125"),
                part("cpu", "CPU · Процессор", "Ryzen 5 7600", "AM5 · DDR5 · 65 Вт",
                        21000, 76, "socket", "AM5", "memory", "DDR5", "tdp", "65"),
                part("cpu", "CPU · Процессор", "Ryzen 7 7800X3D", "AM5 · DDR5 · 120 Вт",
                        38000, 96, "socket", "AM5", "memory", "DDR5", "tdp", "120"),
                part("cpu", "CPU · Процессор", "Core i7-14700K", "LGA1700 · DDR5 · 125 Вт",
                        42000, 90, "socket", "LGA1700", "memory", "DDR5", "tdp", "125"),

                part("gpu", "GPU · Видеокарта", "RTX 4060 8GB", "8 ГБ · 250 мм · 115 Вт",
                        32000, 62, "tdp", "115", "length", "250"),
                part("gpu", "GPU · Видеокарта", "RX 7800 XT 16GB", "16 ГБ · 320 мм · 263 Вт",
                        50000, 80, "tdp", "263", "length", "320"),
                part("gpu", "GPU · Видеокарта", "RTX 4070 12GB", "12 ГБ · 300 мм · 200 Вт",
                        57000, 84, "tdp", "200", "length", "300", "rgb", "true"),
                part("gpu", "GPU · Видеокарта", "RTX 4080 16GB", "16 ГБ · 340 мм · 320 Вт",
                        108000, 96, "tdp", "320", "length", "340", "rgb", "true"),

                part("motherboard", "Мат. плата", "B760 DDR4 mATX", "LGA1700 · B760 · DDR4 · mATX",
                        10000, 55, "socket", "LGA1700", "memory", "DDR4",
                        "form", "mATX", "chipset", "B760"),
                part("motherboard", "Мат. плата", "B650 DDR5 ATX", "AM5 · B650 · DDR5 · ATX",
                        15000, 70, "socket", "AM5", "memory", "DDR5",
                        "form", "ATX", "chipset", "B650"),
                part("motherboard", "Мат. плата", "B760 DDR5 ATX", "LGA1700 · B760 · DDR5 · ATX",
                        16000, 72, "socket", "LGA1700", "memory", "DDR5",
                        "form", "ATX", "chipset", "B760"),
                part("motherboard", "Мат. плата", "X670E DDR5 ATX", "AM5 · X670E · DDR5 · ATX",
                        26000, 92, "socket", "AM5", "memory", "DDR5",
                        "form", "ATX", "chipset", "X670E", "rgb", "true"),
                part("motherboard", "Мат. плата", "Z790 DDR5 ATX", "LGA1700 · Z790 · DDR5 · ATX",
                        28000, 95, "socket", "LGA1700", "memory", "DDR5",
                        "form", "ATX", "chipset", "Z790", "rgb", "true"),

                part("ram_gb", "RAM · Память", "16 ГБ DDR4-3200", "2×8 ГБ · DDR4",
                        4500, 55, "memory", "DDR4"),
                part("ram_gb", "RAM · Память", "32 ГБ DDR5-5600", "2×16 ГБ · DDR5",
                        10000, 85, "memory", "DDR5", "rgb", "true"),
                part("ram_gb", "RAM · Память", "64 ГБ DDR5-6000", "2×32 ГБ · DDR5",
                        20000, 100, "memory", "DDR5", "rgb", "true"),

                part("storage_gb", "SSD · Накопитель", "1 ТБ NVMe", "PCIe 4.0",
                        7500, 55),
                part("storage_gb", "SSD · Накопитель", "2 ТБ NVMe", "PCIe 4.0",
                        13000, 80),
                part("storage_gb", "SSD · Накопитель", "4 ТБ NVMe", "PCIe 4.0",
                        26000, 100),

                part("psu_w", "БП · Питание", "БП 550 Вт", "80+ Bronze",
                        6000, 40, "watts", "550"),
                part("psu_w", "БП · Питание", "БП 750 Вт", "80+ Gold",
                        9500, 70, "watts", "750"),
                part("psu_w", "БП · Питание", "БП 1000 Вт", "80+ Platinum",
                        15000, 95, "watts", "1000"),

                part("cooling", "СО · Охлаждение", "Box cooler", "Воздушный · 60 мм · LGA1700/AM5",
                        1200, 35, "sockets", "LGA1700,AM5", "height", "60"),
                part("cooling", "СО · Охлаждение", "Air tower", "Башенный · 160 мм · LGA1700/AM5",
                        4500, 68, "sockets", "LGA1700,AM5", "height", "160"),
                part("cooling", "СО · Охлаждение", "AIO 240", "Жидкостный · 240 мм · LGA1700/AM5",
                        11000, 92, "sockets", "LGA1700,AM5", "height", "55",
                        "radiator", "240", "rgb", "true"),

                part("case", "Корпус", "micro-ATX", "mATX · GPU 300 мм · кулер 160 мм",
                        4500, 60, "form", "mATX", "supports", "mATX,mini-ITX",
                        "gpuLength", "300", "coolerHeight", "160", "radiator", "240"),
                part("case", "Корпус", "Midi tower", "ATX · GPU 360 мм · кулер 170 мм",
                        8000, 85, "form", "ATX", "supports", "ATX,mATX,mini-ITX",
                        "gpuLength", "360", "coolerHeight", "170", "radiator", "360", "rgb", "true"),
                part("case", "Корпус", "Full tower", "E-ATX · GPU 420 мм · кулер 190 мм",
                        14000, 95, "form", "E-ATX", "supports", "E-ATX,ATX,mATX,mini-ITX",
                        "gpuLength", "420", "coolerHeight", "190", "radiator", "420", "rgb", "true")));

        map.put("laptop", List.of(
                part("cpu", "CPU · Процессор", "Core i5-1340P", "12 ядер · DDR5",
                        12000, 62, "memory", "DDR5"),
                part("cpu", "CPU · Процессор", "Ryzen 7 8845HS", "8 ядер · DDR5",
                        18000, 82, "memory", "DDR5"),
                part("cpu", "CPU · Процессор", "Core i7-13700H", "14 ядер · DDR5",
                        22000, 88, "memory", "DDR5"),

                part("gpu", "GPU · Видеокарта", "Intel Iris Xe", "встроенная",
                        1000, 25),
                part("gpu", "GPU · Видеокарта", "RTX 4050 6GB", "6 ГБ",
                        20000, 60),
                part("gpu", "GPU · Видеокарта", "RTX 4070 8GB", "8 ГБ",
                        30000, 88),

                part("ram_gb", "RAM · Память", "16 ГБ DDR5", "2×8 ГБ",
                        5000, 65, "memory", "DDR5"),
                part("ram_gb", "RAM · Память", "32 ГБ DDR5", "2×16 ГБ",
                        9000, 90, "memory", "DDR5"),

                part("storage_gb", "SSD · Накопитель", "512 ГБ NVMe", "PCIe 4.0",
                        4000, 55),
                part("storage_gb", "SSD · Накопитель", "1 ТБ NVMe", "PCIe 4.0",
                        6500, 85),
                part("storage_gb", "SSD · Накопитель", "2 ТБ NVMe", "PCIe 4.0",
                        10000, 100),

                part("screen", "Экран", "15.6\" IPS 60Hz", "Full HD",
                        6000, 50),
                part("screen", "Экран", "15.6\" IPS 120Hz", "Full HD",
                        9000, 78),
                part("screen", "Экран", "16\" OLED 120Hz", "2.8K",
                        12000, 100),

                part("battery_mah", "Батарея", "5000 мА·ч", "≈ 6 ч",
                        4000, 55),
                part("battery_mah", "Батарея", "7000 мА·ч", "≈ 9 ч",
                        5500, 85),
                part("battery_mah", "Батарея", "9000 мА·ч", "≈ 12 ч",
                        7000, 100),

                part("weight_kg", "Вес", "2.1 кг", "портативный",
                        3000, 70),
                part("weight_kg", "Вес", "1.7 кг", "лёгкий",
                        4500, 88),
                part("weight_kg", "Вес", "1.3 кг", "ультрапортативный",
                        6000, 100)));

        return Map.copyOf(map);
    }
}
