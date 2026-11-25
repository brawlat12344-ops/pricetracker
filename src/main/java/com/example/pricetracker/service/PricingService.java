package com.example.pricetracker.service;

import com.example.pricetracker.domain.Alert;
import com.example.pricetracker.domain.Product;
import com.example.pricetracker.domain.PriceSnapshot;
import com.example.pricetracker.dto.PollResponse;
import com.example.pricetracker.provider.PriceProvider;
import com.example.pricetracker.provider.PriceResult;
import com.example.pricetracker.provider.ProviderRegistry;
import com.example.pricetracker.repo.AlertRepository;
import com.example.pricetracker.repo.PriceSnapshotRepository;
import com.example.pricetracker.repo.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final ProductRepository products;
    private final PriceSnapshotRepository snapshots;
    private final AlertRepository alerts;
    private final ProviderRegistry registry;
    private final JdbcTemplate jdbc;
    private final String providerName;
    private final long intervalMs;

    public PricingService(ProductRepository products,
                          PriceSnapshotRepository snapshots,
                          AlertRepository alerts,
                          ProviderRegistry registry,
                          JdbcTemplate jdbc,
                          @Value("${pricetracker.provider:mock}") String providerName,
                          @Value("${pricetracker.poll-interval-ms:60000}") long intervalMs) {
        this.products = products;
        this.snapshots = snapshots;
        this.alerts = alerts;
        this.registry = registry;
        this.jdbc = jdbc;
        this.providerName = providerName;
        this.intervalMs = intervalMs;
    }

    @Transactional
    public PollResponse poll(UUID productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        Instant now = Instant.now();
        Instant bucket = bucket(now);
        PriceProvider provider = registry.get(providerName);
        PriceResult result = provider.fetch(product.getUrl());

        int inserted = snapshots.insertIgnore(
                productId,
                result.price(),
                result.currency(),
                Timestamp.from(now),
                Timestamp.from(bucket),
                provider.name());

        if (inserted == 0) {
            PriceSnapshot existing = snapshots.findByProductIdAndPeriodStart(productId, bucket)
                    .orElseThrow(() -> new IllegalStateException("Unique conflict without a row"));
            log.info("snapshot_duplicate_skipped product_id={} period_start={}",
                    productId, bucket);
            return new PollResponse(existing.getId(), productId, existing.getPrice(),
                    existing.getProvider(), false, false, bucket);
        }

        Long snapshotId = jdbc.queryForObject(
                "SELECT id FROM price_snapshots WHERE product_id = ? AND period_start = ?",
                Long.class, productId, Timestamp.from(bucket));
        if (snapshotId == null) {
            throw new IllegalStateException("Inserted snapshot not found");
        }

        boolean alertCreated = false;
        BigDecimal threshold = product.getAlertThreshold();
        if (result.price().compareTo(threshold) <= 0) {
            alerts.save(new Alert(productId, snapshotId, result.price(), threshold));
            alertCreated = true;
        }

        log.info("snapshot_created product_id={} snapshot_id={} price={} provider={} alert={}",
                productId, snapshotId, result.price(), provider.name(), alertCreated);
        return new PollResponse(snapshotId, productId, result.price(), provider.name(),
                true, alertCreated, bucket);
    }

    /** Truncate to the configured scheduler bucket: the idempotency key. */
    private Instant bucket(Instant instant) {
        long stepMillis = Math.max(intervalMs, 1);
        return Instant.ofEpochMilli((instant.toEpochMilli() / stepMillis) * stepMillis)
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
