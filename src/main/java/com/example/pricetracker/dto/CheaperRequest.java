package com.example.pricetracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CheaperRequest(
        @NotBlank @Size(max = 2048) String url,
        String category,
        @Size(max = 255) String title,
        @Min(1) @Max(10) Integer limit,
        boolean crossStore,
        @Min(1) BigDecimal sourcePrice) {

    public CheaperRequest(String url, String category, String title, Integer limit, boolean crossStore) {
        this(url, category, title, limit, crossStore, null);
    }

    public String categoryOrPc() {
        return category == null || category.isBlank() ? "pc" : category.toLowerCase();
    }

    public int limitOrDefault() {
        return limit == null || limit <= 0 ? 10 : Math.min(limit, 10);
    }

    /** Цена, которую пользователь видит на странице: точнее любой оценки по комплектации. */
    public BigDecimal knownSourcePrice() {
        return sourcePrice == null || sourcePrice.signum() <= 0 ? null : sourcePrice;
    }
}
