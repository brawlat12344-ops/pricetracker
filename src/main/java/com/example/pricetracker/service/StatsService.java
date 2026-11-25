package com.example.pricetracker.service;

import com.example.pricetracker.domain.PriceSnapshot;
import com.example.pricetracker.dto.SnapshotResponse;
import com.example.pricetracker.dto.StatsPoint;
import com.example.pricetracker.dto.TopDrop;
import com.example.pricetracker.repo.PriceSnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StatsService {

    private final ProductService products;
    private final PriceSnapshotRepository snapshots;
    private final NamedParameterJdbcTemplate jdbc;

    public StatsService(ProductService products,
                        PriceSnapshotRepository snapshots,
                        NamedParameterJdbcTemplate jdbc) {
        this.products = products;
        this.snapshots = snapshots;
        this.jdbc = jdbc;
    }

    public List<StatsPoint> daily(UUID userId, UUID productId, int days) {
        products.owned(userId, productId);
        Instant now = Instant.now();
        Instant from = now.minus(days, ChronoUnit.DAYS);
        return jdbc.query("""
                SELECT date_trunc('day', captured_at AT TIME ZONE 'UTC')::date AS day,
                       min(price)           AS min_price,
                       max(price)           AS max_price,
                       round(avg(price), 2) AS avg_price,
                       count(*)             AS samples
                FROM price_snapshots
                WHERE product_id = :productId
                  AND captured_at >= :from
                  AND captured_at <  :to
                GROUP BY 1
                ORDER BY 1
                """, new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(now)),
                (rs, rowNum) -> new StatsPoint(
                        rs.getDate("day").toLocalDate(),
                        rs.getBigDecimal("min_price"),
                        rs.getBigDecimal("max_price"),
                        rs.getBigDecimal("avg_price"),
                        rs.getLong("samples")));
    }

    public List<TopDrop> topDrops(UUID userId, int days, int limit) {
        Instant now = Instant.now();
        Instant from = now.minus(days, ChronoUnit.DAYS);
        return jdbc.query("""
                WITH bounds AS (
                    SELECT product_id,
                           (array_agg(price ORDER BY captured_at))[1]      AS first_price,
                           (array_agg(price ORDER BY captured_at DESC))[1] AS last_price
                    FROM price_snapshots
                    WHERE captured_at >= :from AND captured_at < :to
                    GROUP BY product_id
                )
                SELECT p.id AS product_id, p.title,
                       b.first_price, b.last_price,
                       round(b.first_price - b.last_price, 2) AS drop_amount,
                       round(100 * (b.first_price - b.last_price) / b.first_price, 2) AS drop_pct
                FROM bounds b JOIN products p ON p.id = b.product_id
                WHERE p.user_id = :userId AND b.last_price < b.first_price
                ORDER BY drop_pct DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(now))
                .addValue("limit", limit),
                (rs, rowNum) -> new TopDrop(
                        UUID.fromString(rs.getString("product_id")),
                        rs.getString("title"),
                        rs.getBigDecimal("first_price"),
                        rs.getBigDecimal("last_price"),
                        rs.getBigDecimal("drop_amount"),
                        rs.getBigDecimal("drop_pct")));
    }

    public List<SnapshotResponse> snapshots(UUID userId, UUID productId, int limit) {
        products.owned(userId, productId);
        return snapshots.findByProductIdOrderByCapturedAtDesc(productId, PageRequest.of(0, limit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SnapshotResponse toResponse(PriceSnapshot snapshot) {
        return new SnapshotResponse(snapshot.getId(), snapshot.getPrice(), snapshot.getCurrency(),
                snapshot.getCapturedAt(), snapshot.getProvider());
    }
}
