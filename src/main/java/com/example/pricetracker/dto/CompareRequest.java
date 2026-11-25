package com.example.pricetracker.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CompareRequest(
        @NotEmpty @Size(min = 2, max = 4) List<UUID> productIds,
        @Size(max = 255) String name) {

    public String nameOrDefault() {
        return name == null || name.isBlank() ? "Сравнение товаров" : name;
    }
}
