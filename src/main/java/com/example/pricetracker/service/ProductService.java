package com.example.pricetracker.service;

import com.example.pricetracker.domain.Alert;
import com.example.pricetracker.domain.Product;
import com.example.pricetracker.dto.AlertResponse;
import com.example.pricetracker.dto.Paginated;
import com.example.pricetracker.dto.PollResponse;
import com.example.pricetracker.dto.ProductPatchRequest;
import com.example.pricetracker.dto.ProductRequest;
import com.example.pricetracker.dto.ProductResponse;
import com.example.pricetracker.repo.AlertRepository;
import com.example.pricetracker.repo.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final AlertRepository alerts;
    private final PricingService pricing;
    private final NamedParameterJdbcTemplate jdbc;

    public ProductService(ProductRepository products,
                          AlertRepository alerts,
                          PricingService pricing,
                          NamedParameterJdbcTemplate jdbc) {
        this.products = products;
        this.alerts = alerts;
        this.pricing = pricing;
        this.jdbc = jdbc;
    }

    public Product owned(UUID userId, UUID productId) {
        return products.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public Page<ProductResponse> list(UUID userId, Pageable pageable) {
        Page<Product> page = products.findByUserId(userId, pageable);
        List<UUID> ids = page.getContent().stream().map(Product::getId).toList();
        Map<UUID, BigDecimal> latest = latestPrices(ids);
        Map<UUID, Long> unread = unreadCounts(ids);
        return page.map(p -> toResponse(p, latest.get(p.getId()), unread.getOrDefault(p.getId(), 0L)));
    }

    public ProductResponse get(UUID userId, UUID productId) {
        Product product = owned(userId, productId);
        Map<UUID, BigDecimal> latest = latestPrices(List.of(productId));
        Map<UUID, Long> unread = unreadCounts(List.of(productId));
        return toResponse(product, latest.get(productId), unread.getOrDefault(productId, 0L));
    }

    @Transactional
    public ProductResponse create(UUID userId, ProductRequest request) {
        validateUrl(request.url());
        Product saved = products.save(new Product(
                userId, request.url().trim(), request.title().trim(), request.alertThreshold()));
        return toResponse(saved, null, 0L);
    }

    @Transactional
    public ProductResponse update(UUID userId, UUID productId, ProductPatchRequest patch) {
        Product product = owned(userId, productId);
        if (patch.title() != null) {
            product.setTitle(patch.title());
        }
        if (patch.alertThreshold() != null) {
            product.setAlertThreshold(patch.alertThreshold());
        }
        if (patch.active() != null) {
            product.setActive(patch.active());
        }
        return toResponse(product, null, 0L);
    }

    @Transactional
    public void delete(UUID userId, UUID productId) {
        products.delete(owned(userId, productId));
    }

    @Transactional // запись снимка/алерта не в read-only транзакции
    public PollResponse poll(UUID userId, UUID productId) {
        owned(userId, productId);
        return pricing.poll(productId);
    }

    public Paginated<AlertResponse> alerts(UUID userId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<AlertResponse> items = jdbc.query("""
                SELECT a.id, a.product_id, p.title, a.price, a.threshold, a.is_read, a.created_at
                FROM alerts a JOIN products p ON p.id = a.product_id
                WHERE p.user_id = :userId AND NOT a.is_read
                ORDER BY a.created_at DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new AlertResponse(
                rs.getLong("id"),
                UUID.fromString(rs.getString("product_id")),
                rs.getString("title"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("threshold"),
                rs.getBoolean("is_read"),
                rs.getTimestamp("created_at").toInstant()));

        long total = jdbc.queryForObject("""
                SELECT count(*) FROM alerts a JOIN products p ON p.id = a.product_id
                WHERE p.user_id = :userId AND NOT a.is_read
                """, new MapSqlParameterSource("userId", userId), Long.class);
        return new Paginated<>(items, total, pageable.getPageNumber(), pageable.getPageSize());
    }

    @Transactional
    public void markAlertRead(UUID userId, long alertId) {
        Long ownedCount = jdbc.queryForObject("""
                SELECT count(*) FROM alerts a JOIN products p ON p.id = a.product_id
                WHERE a.id = :alertId AND p.user_id = :userId
                """, new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("userId", userId), Long.class);
        if (ownedCount == null || ownedCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        alert.setRead(true);
    }

    private ProductResponse toResponse(Product p, BigDecimal latestPrice, long unread) {
        return new ProductResponse(p.getId(), p.getTitle(), p.getUrl(), p.getAlertThreshold(),
                p.isActive(), p.getCreatedAt(), latestPrice, unread);
    }

    private Map<UUID, BigDecimal> latestPrices(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                SELECT DISTINCT ON (product_id) product_id, price
                FROM price_snapshots
                WHERE product_id IN (:ids)
                ORDER BY product_id, captured_at DESC
                """, new MapSqlParameterSource("ids", ids), rs -> {
            Map<UUID, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                result.put(UUID.fromString(rs.getString("product_id")), rs.getBigDecimal("price"));
            }
            return result;
        });
    }

    private Map<UUID, Long> unreadCounts(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                SELECT product_id, count(*) AS cnt
                FROM alerts
                WHERE NOT is_read AND product_id IN (:ids)
                GROUP BY product_id
                """, new MapSqlParameterSource("ids", ids), rs -> {
            Map<UUID, Long> result = new HashMap<>();
            while (rs.next()) {
                result.put(UUID.fromString(rs.getString("product_id")), rs.getLong("cnt"));
            }
            return result;
        });
    }

    private void validateUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.matches("(?i)https?") || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL must be an absolute http(s) URL without credentials");
        }
    }
}
