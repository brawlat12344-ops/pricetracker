package com.example.pricetracker.dto;

/** Запрос эксперта по ноутбукам: бюджет, задачи, приоритеты и требования к конфигурации. */
public record LaptopRequest(
        Integer budget,
        String purpose,
        String priority,
        String screen,
        String os,
        String requirements,
        String region) {

    private static final String PURPOSES = "|study|work|game|montage|programming|balanced|";
    private static final String PRIORITIES = "|performance|battery|weight|screen|silence|";
    private static final String SCREENS = "|any|13_14|15_16|17_plus|";
    private static final String OSES = "|any|windows|macos|";

    public String purposeOrBalanced() {
        if (purpose == null) {
            return "balanced";
        }
        String value = purpose.toLowerCase();
        return PURPOSES.contains("|" + value + "|") ? value : "balanced";
    }

    public String priorityOrPerformance() {
        if (priority == null) {
            return "performance";
        }
        String value = priority.toLowerCase();
        return PRIORITIES.contains("|" + value + "|") ? value : "performance";
    }

    public String screenOrAny() {
        if (screen == null || screen.isBlank()) {
            return "any";
        }
        String value = screen.toLowerCase();
        return SCREENS.contains("|" + value + "|") ? value : "any";
    }

    public String osOrAny() {
        if (os == null || os.isBlank()) {
            return "any";
        }
        String value = os.toLowerCase();
        return OSES.contains("|" + value + "|") ? value : "any";
    }

    public String requirementsOrEmpty() {
        return requirements == null ? "" : requirements.trim();
    }

    public String regionOrRussia() {
        return region == null || region.isBlank() ? "Россия" : region.trim();
    }

    public int budgetOrDefault() {
        return budget == null || budget < 30000 ? 90000 : Math.min(budget, 500000);
    }
}
