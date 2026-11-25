package com.example.pricetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 32)
    private String category = "laptop";

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String attributes = "{}";

    @Column(name = "alert_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal alertThreshold;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Product() {
    }

    public Product(UUID userId, String url, String title, BigDecimal alertThreshold,
                   String category, String attributes) {
        this.userId = userId;
        this.url = url;
        this.title = title;
        this.alertThreshold = alertThreshold;
        this.category = category;
        this.attributes = attributes;
    }

    @PreUpdate
    void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getAttributes() {
        return attributes;
    }

    public BigDecimal getAlertThreshold() {
        return alertThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAlertThreshold(BigDecimal alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
