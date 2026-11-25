package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatsPoint(
        LocalDate day,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal avgPrice,
        long samples) {
}
