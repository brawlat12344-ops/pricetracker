package com.example.pricetracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalogRequest(
        @NotBlank @Size(max = 2048) String url,
        String category,
        @Size(max = 255) String title,
        @Min(1) @Max(10) int limit,
        Boolean web) {

    public String categoryOrPc() {
        return category == null || category.isBlank() ? "pc" : category.toLowerCase();
    }

    public int limitOrDefault() {
        return limit <= 0 ? 10 : limit;
    }
}
