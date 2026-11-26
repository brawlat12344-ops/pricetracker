package com.example.pricetracker.dto;

/** Запрос локального конфигуратора: тема, цель и бюджет. */
public record BuildRequest(String category, String purpose, Integer budget) {

    public String categoryOrPc() {
        return "laptop".equalsIgnoreCase(category) ? "laptop" : "pc";
    }

    public String purposeOrBalanced() {
        if (purpose == null) {
            return "balanced";
        }
        String value = purpose.toLowerCase();
        return value.equals("game") || value.equals("work") || value.equals("study")
                ? value : "balanced";
    }

    public int budgetOrDefault() {
        return budget == null || budget < 20000 ? 120000 : Math.min(budget, 1000000);
    }
}
