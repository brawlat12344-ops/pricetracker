package com.example.pricetracker.repo;

import com.example.pricetracker.domain.PriceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    /**
     * Idempotent insert: the UNIQUE(product_id, period_start) constraint turns a
     * repeated scheduler tick into 0 inserted rows instead of a duplicate snapshot.
     */
    @Modifying
    @Query(value = """
            INSERT INTO price_snapshots
                (id, product_id, price, currency, captured_at, period_start, provider)
            VALUES
                (nextval('price_snapshots_id_seq'), :productId, :price, :currency,
                 :capturedAt, :periodStart, :provider)
            ON CONFLICT (product_id, period_start) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(
            @Param("productId") UUID productId,
            @Param("price") BigDecimal price,
            @Param("currency") String currency,
            @Param("capturedAt") Timestamp capturedAt,
            @Param("periodStart") Timestamp periodStart,
            @Param("provider") String provider);

    Optional<PriceSnapshot> findByProductIdAndPeriodStart(UUID productId, Instant periodStart);

    Page<PriceSnapshot> findByProductIdOrderByCapturedAtDesc(UUID productId, Pageable pageable);
}
