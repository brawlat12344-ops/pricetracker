package com.example.pricetracker.dto;

/** Запрос экспертного конфигуратора: бюджет, задача, приоритет и локальные пожелания. */
public record BuildRequest(
        String category,
        String purpose,
        String priority,
        String wishes,
        String region,
        Integer budget) {

    private static final String PURPOSES = "|game|work|montage|3d|streaming|study|balanced|";
    private static final String PRIORITIES = "|performance|silence|compact|upgrade|";

    public String categoryOrPc() {
        return "laptop".equalsIgnoreCase(category) ? "laptop" : "pc";
    }

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

    public String wishesOrEmpty() {
        return wishes == null ? "" : wishes.trim();
    }

    public String regionOrRussia() {
        return region == null || region.isBlank() ? "Россия" : region.trim();
    }

    public int budgetOrDefault() {
        return budget == null || budget < 20000 ? 120000 : Math.min(budget, 1000000);
    }
}
