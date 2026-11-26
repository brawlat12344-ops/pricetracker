package com.example.pricetracker.laptop;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Справочник конкретных моделей ноутбуков с точными конфигурациями.
 * Бенчмарки и тесты автономности — справочные ориентиры, а не замер в момент запроса.
 */
@Component
public class LaptopCatalog {

    public record Model(
            String sku, String name, String config,
            String cpu, String gpu, String ram, int ramGb, String ssd,
            String display, String screenClass, String os,
            double cpuMark, double gpuMark, double batteryHours,
            String brightness, String coverage, double weightKg,
            String ports, String upgrade, String pros, String cons,
            String pitfalls, String forecast, BigDecimal basePrice,
            boolean discreteGpu, boolean thunderbolt, boolean quiet) {
    }

    private static final List<Model> MODELS = List.of(
            new Model(
                    "MRXN3RU/A", "Apple MacBook Air 13 M3",
                    "M3 8-core CPU / 10-core GPU · 8 ГБ unified · SSD 256 ГБ · 13.6″ Liquid Retina",
                    "Apple M3", "10-core GPU (встроенная)", "8 ГБ unified (распаяна)", 8, "256 ГБ SSD",
                    "13.6″ Liquid Retina 2560×1664", "13_14", "macos",
                    1450, 2100, 15.0, "500 нит", "P3 100%", 1.24,
                    "2×Thunderbolt / USB4, MagSafe, 3.5 мм", "нет: память и SSD распаяны",
                    "тихий (вентилятора нет), лучшая автономность, экран",
                    "8 ГБ unified на весь срок службы, дорогой апгрейд",
                    "троттлинг в длинных рендерах; 256 ГБ быстро заполняется",
                    "3–5 лет для учёбы и работы; при 8 ГБ — риск через 3 года",
                    BigDecimal.valueOf(105000), false, true, true),

            new Model(
                    "MRYN3RU/A", "Apple MacBook Air 15 M3",
                    "M3 8-core CPU / 10-core GPU · 8 ГБ unified · SSD 256 ГБ · 15.3″ Liquid Retina",
                    "Apple M3", "10-core GPU (встроенная)", "8 ГБ unified (распаяна)", 8, "256 ГБ SSD",
                    "15.3″ Liquid Retina 2880×1864", "15_16", "macos",
                    1450, 2100, 14.0, "500 нит", "P3 100%", 1.51,
                    "2×Thunderbolt / USB4, MagSafe, 3.5 мм", "нет: память и SSD распаяны",
                    "большой экран, тишина, автономность",
                    "8 ГБ unified, высокая цена за конфигурацию",
                    "троттлинг под нагрузкой, слабый SSD для монтажа 4K",
                    "3–5 лет для учёбы/работы, не для тяжёлого монтажа",
                    BigDecimal.valueOf(135000), false, true, true),

            new Model(
                    "X1605VA-MB552W", "ASUS Vivobook 16 X1605VA",
                    "Core i5-13420H · Intel UHD/Iris Xe · 16 ГБ DDR5 · SSD 512 ГБ · 16″ IPS FHD",
                    "Intel Core i5-13420H", "Intel UHD Graphics", "16 ГБ DDR5", 16, "512 ГБ SSD",
                    "16″ IPS 1920×1200 60 Гц", "15_16", "windows",
                    1250, 850, 7.0, "300 нит", "sRGB ~60%", 1.88,
                    "USB-C, HDMI, 2×USB-A", "2×SO-DIMM, 1×M.2",
                    "большой экран, 16 ГБ, легко апгрейдить",
                    "тусклая матрица, заметный шум под нагрузкой",
                    "слабая матрица и падение частоты при долгой нагрузке",
                    "3 года уверенно; 5 — после замены SSD и термопасты",
                    BigDecimal.valueOf(62000), false, false, false),

            new Model(
                    "82XF0039RK", "Lenovo IdeaPad Slim 5 16 Gen 9",
                    "Ryzen 7 8845HS · Radeon 780M · 16 ГБ DDR5 · SSD 512 ГБ · 16″ WUXGA IPS",
                    "AMD Ryzen 7 8845HS", "Radeon 780M (встроенная)", "16 ГБ DDR5", 16, "512 ГБ SSD",
                    "16″ IPS 1920×1200 60 Гц", "15_16", "windows",
                    1600, 1400, 9.0, "350 нит", "sRGB 100%", 1.79,
                    "2×USB-C, HDMI, 2×USB-A", "SO-DIMM + M.2 2280",
                    "сильный CPU, хороший экран, автономность",
                    "встроенной графики мало для современных игр",
                    "в дешёвых партиях память может быть одноканальной — уточняйте",
                    "4–5 лет для работы, учёбы и программирования",
                    BigDecimal.valueOf(78000), false, false, true),

            new Model(
                    "A515-58P-56AZ", "Acer Aspire 5 A515-58P",
                    "Core i5-13420H · Intel UHD · 16 ГБ DDR5 · SSD 512 ГБ · 15.6″ IPS FHD",
                    "Intel Core i5-13420H", "Intel UHD Graphics", "16 ГБ DDR5", 16, "512 ГБ SSD",
                    "15.6″ IPS 1920×1080 60 Гц", "15_16", "windows",
                    1220, 820, 6.5, "250 нит", "sRGB ~55%", 1.77,
                    "USB-C, HDMI, 3×USB-A", "2×SO-DIMM, 2×M.2",
                    "низкая цена, хороший апгрейд-потенциал",
                    "тусклый экран, шум вентилятора",
                    "плохая матрица и слабая автономность — проверьте по отзывам",
                    "3 года; дальше — только с заменой SSD",
                    BigDecimal.valueOf(56000), false, false, false),

            new Model(
                    "14-ey0020ua", "HP Pavilion Plus 14",
                    "Ryzen 7 7840U · Radeon 780M · 16 ГБ LPDDR5 · SSD 512 ГБ · 14″ OLED 2.8K",
                    "AMD Ryzen 7 7840U", "Radeon 780M (встроенная)", "16 ГБ LPDDR5 (распаяна)", 16, "512 ГБ SSD",
                    "14″ OLED 2880×1800 90 Гц", "13_14", "windows",
                    1550, 1450, 8.0, "400 нит", "DCI-P3 100%", 1.44,
                    "2×USB-C, HDMI, 2×USB-A", "память распаяна, SSD M.2",
                    "отличный OLED, лёгкий корпус, быстрый CPU",
                    "память нельзя увеличить, только встроенная графика",
                    "возможное выгорание OLED; троттлинг в тонком корпусе",
                    "4 года для работы и программирования",
                    BigDecimal.valueOf(89000), false, false, true),

            new Model(
                    "FA507NV-LP023W", "ASUS TUF Gaming A15",
                    "Ryzen 7 7735HS · RTX 4060 8 ГБ · 16 ГБ DDR5 · SSD 512 ГБ · 15.6″ IPS 144 Гц",
                    "AMD Ryzen 7 7735HS", "NVIDIA RTX 4060 8 ГБ", "16 ГБ DDR5", 16, "512 ГБ SSD",
                    "15.6″ IPS 1920×1080 144 Гц", "15_16", "windows",
                    1500, 7200, 6.0, "250 нит", "sRGB ~62%", 2.20,
                    "USB-C, HDMI, RJ-45, 3×USB-A", "2×SO-DIMM, 2×M.2",
                    "дискретная GPU, продуманное охлаждение, апгрейд",
                    "тусклая матрица, шумный, тяжёлый",
                    "вентиляторы громкие в играх; матрицу лучше заменить внешним монитором",
                    "5 лет для игр в 1080p при уходе за охлаждением",
                    BigDecimal.valueOf(118000), true, false, false),

            new Model(
                    "83DV007GRK", "Lenovo LOQ 15IRX9",
                    "Core i5-13450HX · RTX 4050 6 ГБ · 16 ГБ DDR5 · SSD 512 ГБ · 15.6″ IPS 144 Гц",
                    "Intel Core i5-13450HX", "NVIDIA RTX 4050 6 ГБ", "16 ГБ DDR5", 16, "512 ГБ SSD",
                    "15.6″ IPS 1920×1080 144 Гц", "15_16", "windows",
                    1800, 5600, 5.0, "300 нит", "sRGB 100%", 2.19,
                    "USB-C, HDMI, RJ-45", "2×SO-DIMM, M.2",
                    "баланс цены и GPU, яркая матрица sRGB",
                    "вес и шум под нагрузкой",
                    "короткое время работы от батареи; проверяйте партию прошивки BIOS",
                    "4 года для игр и учёбы",
                    BigDecimal.valueOf(98000), true, false, false),

            new Model(
                    "B14V-1267RU", "MSI Katana 15 B14V",
                    "Core i7-14700HX · RTX 4070 8 ГБ · 16 ГБ DDR5 · SSD 1 ТБ · 15.6″ IPS 144 Гц",
                    "Intel Core i7-14700HX", "NVIDIA RTX 4070 8 ГБ", "16 ГБ DDR5", 16, "1 ТБ SSD",
                    "15.6″ IPS 1920×1080 144 Гц", "15_16", "windows",
                    2400, 8600, 4.5, "250 нит", "sRGB ~55%", 2.25,
                    "USB-C, HDMI, RJ-45", "2×SO-DIMM, 2×M.2",
                    "максимум производительности, 1 ТБ SSD",
                    "очень шумный, слабая матрица, маленькая батарея",
                    "троттлинг в тонком корпусе; требуется замена термопасты через 2 года",
                    "5 лет для игр, но с обслуживанием охлаждения",
                    BigDecimal.valueOf(155000), true, false, false),

            new Model(
                    "SFG14-71-58ED", "Acer Swift Go 14",
                    "Core Ultra 5 125U · Intel Graphics · 16 ГБ LPDDR5 · SSD 512 ГБ · 14″ OLED 2.8K",
                    "Intel Core Ultra 5 125U", "Intel Graphics (встроенная)", "16 ГБ LPDDR5 (распаяна)", 16, "512 ГБ SSD",
                    "14″ OLED 2880×1800 90 Гц", "13_14", "windows",
                    1400, 1200, 9.0, "500 нит", "DCI-P3 100%", 1.32,
                    "2×Thunderbolt 4, HDMI, USB-A", "память распаяна, SSD M.2",
                    "лёгкий, OLED, Thunderbolt 4, автономность",
                    "только встроенная графика, память не апгрейдится",
                    "OLED может выгорать; игровая производительность ограничена",
                    "4 года для работы и учёбы",
                    BigDecimal.valueOf(87000), false, true, true)
    );

    public List<Model> all() {
        return MODELS;
    }
}
