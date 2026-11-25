package com.example.pricetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_snapshots", uniqueConstraints = @UniqueConstraint(
        name = "uq_snapshot_product_period", columnNames = {"product_id", "period_start"}))
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "RUB";

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(nullable = false, length = 32)
    private String provider;

    public PriceSnapshot() {
    }

    public PriceSnapshot(UUID productId, BigDecimal price, String currency,
                         Instant capturedAt, Instant periodStart, String provider) {
        this.productId = productId;
        this.price = price;
        this.currency = currency;
        this.capturedAt = capturedAt;
        this.periodStart = periodStart;
        this.provider = provider;
    }

    public Long getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public String getProvider() {
        return provider;
    }
}
