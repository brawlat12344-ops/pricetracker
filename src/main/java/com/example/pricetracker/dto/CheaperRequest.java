package com.example.pricetracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheaperRequest(
        @NotBlank @Size(max = 2048) String url,
        String category,
        @Size(max = 255) String title,
        @Min(1) @Max(10) Integer limit,
        boolean crossStore) {

    public String categoryOrPc() {
        return category == null || category.isBlank() ? "pc" : category.toLowerCase();
    }

    public int limitOrDefault() {
        return limit == null || limit <= 0 ? 10 : Math.min(limit, 10);
    }
}
