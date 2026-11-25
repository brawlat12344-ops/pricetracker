package com.example.pricetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal threshold;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Alert() {
    }

    public Alert(UUID productId, Long snapshotId, BigDecimal price, BigDecimal threshold) {
        this.productId = productId;
        this.snapshotId = snapshotId;
        this.price = price;
        this.threshold = threshold;
    }

    public Long getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
