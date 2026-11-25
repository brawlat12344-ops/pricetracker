package com.example.pricetracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductPatchRequest(
        @Size(min = 1, max = 255) String title,
        @DecimalMin("0.01") BigDecimal alertThreshold,
        Boolean active) {
}
