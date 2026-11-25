package com.example.pricetracker.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopDrop(
        UUID productId,
        String title,
        BigDecimal firstPrice,
        BigDecimal lastPrice,
        BigDecimal dropAmount,
        BigDecimal dropPct) {
}
