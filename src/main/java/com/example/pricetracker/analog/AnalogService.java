package com.example.pricetracker.analog;

import com.example.pricetracker.analog.AnalogSource.Candidate;
import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.compare.CategoryCatalog.CategoryDef;
import com.example.pricetracker.compare.ComparisonService;
import com.example.pricetracker.compare.ComparisonService.Evaluation;
import com.example.pricetracker.domain.Product;
import com.example.pricetracker.dto.AnalogRequest;
import com.example.pricetracker.dto.AnalogResponse;
import com.example.pricetracker.dto.CompareItem;
import com.example.pricetracker.provider.PriceProvider;
import com.example.pricetracker.provider.ProviderRegistry;
import com.example.pricetracker.provider.SpecProviderRegistry;
import com.example.pricetracker.repo.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Поиск аналогов по одной ссылке. Источники: демо-каталог и реальный поиск.
 * Найденные карточки НЕ добавляются в отслеживание — результат живёт только на странице.
 */
@Service
public class AnalogService {

    private final Map<String, AnalogSource> sources;
    private final ProviderRegistry priceProviders;
    private final SpecProviderRegistry specProviders;
    private final ComparisonService comparisons;
    private final CategoryCatalog catalog;
    private final ProductRepository products;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String priceProviderName;
    private final String specProviderName;
    private final boolean webSearchDefault;

    public AnalogService(List<AnalogSource> sourceBeans,
                         ProviderRegistry priceProviders,
                         SpecProviderRegistry specProviders,
                         ComparisonService comparisons,
                         CategoryCatalog catalog,
                         ProductRepository products,
                         NamedParameterJdbcTemplate jdbc,
                         ObjectMapper mapper,
                         @Value("${pricetracker.provider:mock}") String priceProviderName,
                         @Value("${pricetracker.spec-provider:mock}") String specProviderName,
                         @Value("${pricetracker.analog-web-search:false}") boolean webSearchDefault) {
        this.sources = sourceBeans.stream()
                .collect(Collectors.toMap(AnalogSource::name, Function.identity()));
        this.priceProviders = priceProviders;
        this.specProviders = specProviders;
        this.comparisons = comparisons;
        this.catalog = catalog;
        this.products = products;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.priceProviderName = priceProviderName;
        this.specProviderName = specProviderName;
        this.webSearchDefault = webSearchDefault;
    }

    public AnalogResponse find(UUID userId, AnalogRequest request) {
        String category = request.categoryOrPc();
        if (!catalog.exists(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная категория: " + category);
        }
        String url = validateUrl(request.url());
        int limit = request.limitOrDefault();
        boolean web = request.web() == null ? webSearchDefault : request.web();

        List<String> warnings = new ArrayList<>();
        Map<String, Candidate> byUrl = new LinkedHashMap<>();

        Candidate source = sourceCandidate(userId, url, category, request.title(), warnings);
        if (source != null) {
            byUrl.put(DemoCatalogSource.normalize(url), source);
        }

        boolean webUsed = false;
        if (web) {
            try {
                for (Candidate candidate : source("web").find(url, category, request.title(), limit)) {
                    add(byUrl, candidate);
                }
                webUsed = true;
            } catch (RuntimeException ex) {
                warnings.add("Реальный поиск недоступен: " + ex.getMessage() + ". Показаны демо-карточки.");
            }
        }

        for (Candidate candidate : source("demo").find(url, category, request.title(), limit)) {
            add(byUrl, candidate);
        }

        List<CompareItem> items = new ArrayList<>();
        for (Candidate candidate : byUrl.values()) {
            Candidate full = candidate.price() == null || candidate.specs().isEmpty()
                    ? enrich(candidate, category, warnings)
                    : candidate;
            if (full == null) {
                continue;
            }
            Evaluation evaluation = comparisons.evaluate(category, full.specs(), full.price());
            UUID id = UUID.nameUUIDFromBytes(full.url().getBytes(StandardCharsets.UTF_8));
            items.add(new CompareItem(id, full.title(), full.url(), full.price(),
                    evaluation.score(), evaluation.valueScore(), full.specs(),
                    evaluation.attributeScores(), evaluation.missingAttributes()));
            if (items.size() >= limit) {
                break;
            }
        }

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не найдено карточек с ценой и характеристиками. Попробуйте другую ссылку.");
        }

        items.sort(Comparator.comparingDouble(CompareItem::valueScore).reversed());
        CompareItem winner = items.get(0);
        int sourceRank = 0;
        for (int i = 0; i < items.size(); i++) {
            if (DemoCatalogSource.normalize(items.get(i).url()).equals(DemoCatalogSource.normalize(url))) {
                sourceRank = i + 1;
                break;
            }
        }

        String verdict = String.format(
                "Лучшее за цену: %s — %.2f балла за 1000 ₽ (цена %s ₽, %.1f баллов за комплектацию). "
                        + "Всего вариантов: %d.%s",
                winner.title(), winner.valueScore(), winner.price(), winner.score(), items.size(),
                sourceRank > 0 ? " Исходная ссылка занимает " + sourceRank + " место." : "");

        CategoryDef definition = catalog.get(category);
        String sourceNames = webUsed ? "демо-каталог + веб-поиск (DuckDuckGo/Bing)" : "демо-каталог";
        return new AnalogResponse(url, category, definition.label(), sourceNames,
                definition.attributes(), items, winner.productId(), verdict, warnings);
    }

    private AnalogSource source(String name) {
        AnalogSource source = sources.get(name);
        if (source == null) {
            throw new IllegalStateException("Analog source not found: " + name);
        }
        return source;
    }

    private void add(Map<String, Candidate> byUrl, Candidate candidate) {
        String key = DemoCatalogSource.normalize(candidate.url());
        if (!key.isBlank() && !byUrl.containsKey(key)) {
            byUrl.put(key, candidate);
        }
    }

    private Candidate sourceCandidate(UUID userId, String url, String category,
                                      String title, List<String> warnings) {
        String name = title == null || title.isBlank() ? "Исходная ссылка" : title.trim();

        Optional<Product> tracked = products.findByUserIdAndUrl(userId, url);
        if (tracked.isPresent()) {
            Product product = tracked.get();
            Map<String, String> specs = parse(product.getAttributes());
            BigDecimal price = latestPrice(product.getId());
            if (price != null && !specs.isEmpty()) {
                return new Candidate(url, title == null || title.isBlank() ? product.getTitle() : name,
                        price, specs);
            }
        }

        try {
            Map<String, String> specs = specProviders.get(specProviderName).fetch(url, category);
            BigDecimal price;
            PriceProvider provider = priceProviders.get(priceProviderName);
            if ("mock".equalsIgnoreCase(provider.name())) {
                price = estimatedPrice(category, specs);
                warnings.add("Провайдер цены работает в демо-режиме: цена исходной ссылки оценена по характеристикам.");
            } else {
                price = provider.fetch(url).price();
            }
            return new Candidate(url, name, price, specs);
        } catch (RuntimeException ex) {
            warnings.add("Не удалось прочитать исходную ссылку: " + ex.getMessage()
                    + ". Сравнение построено по найденным аналогам.");
            return null;
        }
    }

    /** Демо-оценка цены по комплектации: нужна, чтобы mock-цена ~9 000 ₽ не портила сравнение ПК. */
    private BigDecimal estimatedPrice(String category, Map<String, String> specs) {
        double score = comparisons.evaluate(category, specs, BigDecimal.ONE).score();
        double value = Math.max(60000, Math.min(450000, 100000.0 * score / 60.0));
        return BigDecimal.valueOf(Math.round(value / 100) * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private Candidate enrich(Candidate candidate, String category, List<String> warnings) {
        BigDecimal price = candidate.price();
        Map<String, String> specs = candidate.specs();

        if (price == null) {
            try {
                price = priceProviders.get("html").fetch(candidate.url()).price();
            } catch (RuntimeException ex) {
                warnings.add("Нет цены у карточки «" + candidate.title() + "» — она пропущена.");
                return null;
            }
        }
        if (specs.isEmpty()) {
            try {
                specs = specProviders.get("html").fetch(candidate.url(), category);
            } catch (RuntimeException ex) {
                warnings.add("Не удалось прочитать характеристики: " + candidate.title());
            }
        }
        if (specs.isEmpty()) {
            warnings.add("Карточка «" + candidate.title() + "» пропущена: характеристики не найдены.");
            return null;
        }
        return new Candidate(candidate.url(), candidate.title(), price, specs);
    }

    private BigDecimal latestPrice(UUID productId) {
        return jdbc.query(
                "SELECT price FROM price_snapshots WHERE product_id = :productId ORDER BY captured_at DESC LIMIT 1",
                new MapSqlParameterSource("productId", productId),
                rs -> rs.next() ? rs.getBigDecimal(1) : null);
    }

    private String validateUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите URL товара");
        }
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("нужен http(s)-адрес с хостом");
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный URL: " + ex.getMessage());
        }
    }

    private Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
