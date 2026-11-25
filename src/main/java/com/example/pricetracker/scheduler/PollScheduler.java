package com.example.pricetracker.scheduler;

import com.example.pricetracker.domain.Product;
import com.example.pricetracker.repo.ProductRepository;
import com.example.pricetracker.service.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Only active in the separate "worker" container. */
@Profile("worker")
@Component
public class PollScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollScheduler.class);

    private final ProductRepository products;
    private final PricingService pricing;

    public PollScheduler(ProductRepository products, PricingService pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    @Scheduled(fixedDelayString = "${pricetracker.poll-interval-ms:60000}", initialDelay = 0)
    public void pollAll() {
        List<UUID> ids = products.findAll().stream()
                .filter(Product::isActive)
                .map(Product::getId)
                .toList();
        int created = 0;
        int duplicates = 0;
        int failed = 0;
        for (UUID id : ids) {
            try {
                if (pricing.poll(id).created()) {
                    created++;
                } else {
                    duplicates++;
                }
            } catch (Exception ex) { // one bad product must not stop the cycle
                failed++;
                log.warn("product_poll_failed product_id={} error={}", id, ex.getMessage());
            }
        }
        log.info("worker_cycle_done products={} created={} duplicates={} failed={}",
                ids.size(), created, duplicates, failed);
    }
}
