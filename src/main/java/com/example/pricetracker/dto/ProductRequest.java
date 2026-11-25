package com.example.pricetracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String url,
        @NotBlank @Size(max = 255) String title,
        @NotNull @DecimalMin("0.01") BigDecimal alertThreshold) {
}
