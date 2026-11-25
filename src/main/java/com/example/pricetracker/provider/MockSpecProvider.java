package com.example.pricetracker.provider;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Стабильные характеристики для демо и тестов — зависят только от URL и категории. */
@Component
public class MockSpecProvider implements SpecProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Map<String, String> fetch(String url, String category) {
        // Берём биты 2–3 хэша: для близких демо-URL получаются разные вариации.
        int i = Math.floorMod(url.hashCode() >>> 2, 4);
        Map<String, String> specs = new LinkedHashMap<>();

        switch (category) {
            case "laptop" -> {
                specs.put("cpu", new String[]{"Core i5-1340P", "Core i7-13700H", "Ryzen 5 7640U", "Ryzen 7 7840HS"}[i]);
                specs.put("gpu", new String[]{"Iris Xe", "RTX 4060", "RTX 4070", "Radeon 780M"}[i]);
                specs.put("ram_gb", new String[]{"16", "16", "32", "32"}[i]);
                specs.put("storage_gb", new String[]{"512", "1024", "1024", "2048"}[i]);
                specs.put("screen", new String[]{"Full HD 60Hz", "IPS 144Hz", "OLED 120Hz", "4K OLED 120Hz"}[i]);
                specs.put("battery_mah", new String[]{"5200", "6800", "7500", "9000"}[i]);
                specs.put("weight_kg", new String[]{"1.8", "1.6", "1.35", "1.15"}[i]);
            }
            case "phone" -> {
                specs.put("cpu", new String[]{"Snapdragon 7 Gen 3", "Snapdragon 8 Gen 3", "Apple A16", "Dimensity 9300"}[i]);
                specs.put("ram_gb", new String[]{"8", "12", "12", "16"}[i]);
                specs.put("storage_gb", new String[]{"128", "256", "256", "512"}[i]);
                specs.put("camera", new String[]{"50MP", "108MP", "50MP", "200MP"}[i]);
                specs.put("battery_mah", new String[]{"4500", "5000", "4800", "5500"}[i]);
                specs.put("screen", new String[]{"AMOLED 90Hz", "AMOLED 120Hz", "OLED 120Hz", "AMOLED 144Hz"}[i]);
                specs.put("weight_kg", new String[]{"0.21", "0.19", "0.20", "0.22"}[i]);
            }
            case "headphones" -> {
                specs.put("driver", new String[]{"40mm dynamic", "40mm dynamic", "Planar", "Balanced armature"}[i]);
                specs.put("anc", new String[]{"no", "active", "adaptive", "adaptive"}[i]);
                specs.put("battery_h", new String[]{"30", "40", "50", "60"}[i]);
                specs.put("connectivity", new String[]{"BT 5.0", "BT 5.2", "BT 5.3", "BT 5.4"}[i]);
                specs.put("weight_kg", new String[]{"0.32", "0.28", "0.38", "0.25"}[i]);
            }
            case "pc" -> {
                specs.put("cpu", new String[]{"Core i5-14600K", "Core i7-14700K", "Ryzen 5 7600X", "Ryzen 9 7900X"}[i]);
                specs.put("gpu", new String[]{"RTX 4060", "RTX 4070", "RX 7800 XT", "RTX 4090"}[i]);
                specs.put("ram_gb", new String[]{"16", "32", "32", "64"}[i]);
                specs.put("storage_gb", new String[]{"1024", "2048", "2048", "4096"}[i]);
                specs.put("psu_w", new String[]{"650", "750", "850", "1200"}[i]);
                specs.put("cooling", new String[]{"stock", "air", "AIO", "liquid"}[i]);
            }
            default -> throw new SpecProvider.ProviderException("Unknown category: " + category);
        }
        return specs;
    }
}
