package com.example.pricetracker.provider;

import java.math.BigDecimal;

public record PriceResult(BigDecimal price, String currency, String title) {

    public static PriceResult of(BigDecimal price) {
        return new PriceResult(price, "RUB", null);
    }
}
