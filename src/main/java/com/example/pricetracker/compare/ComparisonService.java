package com.example.pricetracker.compare;

import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;
import com.example.pricetracker.compare.CategoryCatalog.CategoryDef;
import com.example.pricetracker.domain.Product;
import com.example.pricetracker.dto.CompareItem;
import com.example.pricetracker.dto.CompareRequest;
import com.example.pricetracker.dto.CompareResponse;
import com.example.pricetracker.dto.SavedComparison;
import com.example.pricetracker.provider.SpecProviderRegistry;
import com.example.pricetracker.repo.ProductRepository;
import com.example.pricetracker.service.PricingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ComparisonService {

    private final ProductRepository products;
    private final NamedParameterJdbcTemplate jdbc;
    private final CategoryCatalog catalog;
    private final SpecProviderRegistry specProviders;
    private final PricingService pricing;
    private final ObjectMapper mapper;
    private final String specProviderName;

    public ComparisonService(ProductRepository products,
                             NamedParameterJdbcTemplate jdbc,
                             CategoryCatalog catalog,
                             SpecProviderRegistry specProviders,
                             PricingService pricing,
                             ObjectMapper mapper,
                             @Value("${pricetracker.spec-provider:mock}") String specProviderName) {
        this.products = products;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.specProviders = specProviders;
        this.pricing = pricing;
        this.mapper = mapper;
        this.specProviderName = specProviderName;
    }

    @Transactional
    public CompareResponse compare(UUID userId, CompareRequest request) {
        List<UUID> ids = request.productIds().stream().distinct().toList();
        if (ids.size() < 2 || ids.size() > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select from 2 to 4 products");
        }

        List<Product> selected = ids.stream()
                .map(id -> products.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)))
                .toList();

        String category = selected.get(0).getCategory();
        if (selected.stream().anyMatch(p -> !category.equals(p.getCategory()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Все товары в сравнении должны быть одной категории");
        }

        CategoryDef definition = catalog.get(category);
        List<CompareItem> items = new ArrayList<>();

        for (Product product : selected) {
            Map<String, String> attributes = fromJson(product.getAttributes());
            if (attributes.isEmpty()) {
                attributes = specProviders.get(specProviderName).fetch(product.getUrl(), category);
                product.setAttributes(toJson(attributes));
            }

            BigDecimal price = latestPrice(product.getId());
            Map<String, Double> scores = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            double total = 0;

            for (AttributeDef attribute : definition.attributes()) {
                double score = attributeScore(attribute, attributes.get(attribute.key()));
                scores.put(attribute.key(), score);
                if (score <= 0) {
                    missing.add(attribute.key());
                }
                total += attribute.weight() / 100.0 * score;
            }

            double value = price.signum() > 0
                    ? total / price.doubleValue() * 1000
                    : 0;

            items.add(new CompareItem(product.getId(), product.getTitle(), product.getUrl(), price,
                    total, value, attributes, scores, missing));
        }

        CompareItem winner = items.stream()
                .max(Comparator.comparingDouble(CompareItem::valueScore))
                .orElseThrow();

        String verdict = String.format(
                "Лучшее соотношение «комплектующие за цену»: %s — %.1f баллов при %s ₽ (показатель %.2f балла за 1000 ₽).",
                winner.title(), winner.score(), winner.price(), winner.valueScore());

        UUID comparisonId = saveComparison(userId, request.nameOrDefault(), category, ids, winner.productId());

        return new CompareResponse(comparisonId, category, definition.label(), definition.attributes(),
                items, winner.productId(), verdict);
    }

    public List<SavedComparison> saved(UUID userId) {
        return jdbc.query("""
                        SELECT id, name, category, product_ids, winner_id, created_at
                        FROM comparisons
                        WHERE user_id = :userId
                        ORDER BY created_at DESC
                        LIMIT 20
                        """,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> new SavedComparison(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("category"),
                        readIds(rs.getString("product_ids")),
                        rs.getString("winner_id") == null ? null : UUID.fromString(rs.getString("winner_id")),
                        rs.getTimestamp("created_at").toInstant()));
    }

    private BigDecimal latestPrice(UUID productId) {
        BigDecimal price = jdbc.query(
                "SELECT price FROM price_snapshots WHERE product_id = :productId ORDER BY captured_at DESC LIMIT 1",
                new MapSqlParameterSource("productId", productId),
                rs -> rs.next() ? rs.getBigDecimal(1) : null);
        return price != null ? price : pricing.poll(productId).price();
    }

    private double attributeScore(AttributeDef def, String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String value = raw.toLowerCase();

        if (!def.textScores().isEmpty()) {
            return def.textScores().entrySet().stream()
                    .filter(entry -> value.contains(entry.getKey()))
                    .mapToDouble(Map.Entry::getValue)
                    .max()
                    .orElse(35);
        }

        try {
            String cleaned = value.replace(',', '.').replaceAll("[^0-9.]", "");
            double number = Double.parseDouble(cleaned);
            if (number <= 0) {
                return 0;
            }
            double score = def.higherBetter()
                    ? number / def.reference() * 100
                    : def.reference() / number * 100;
            return Math.min(100, score);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private UUID saveComparison(UUID userId, String name, String category,
                                List<UUID> productIds, UUID winnerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO comparisons (id, user_id, name, category, product_ids, winner_id)
                        VALUES (:id, :userId, :name, :category, CAST(:productIds AS jsonb), :winnerId)
                        """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("name", name)
                .addValue("category", category)
                .addValue("productIds", writeIds(productIds))
                .addValue("winnerId", winnerId));
        return id;
    }

    private String writeIds(List<UUID> ids) {
        try {
            return mapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize comparison", e);
        }
    }

    private List<UUID> readIds(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<UUID>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJson(Map<String, String> attributes) {
        try {
            return mapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize attributes", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
