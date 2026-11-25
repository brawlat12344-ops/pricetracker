package com.example.pricetracker.store;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.compare.ComparisonService;
import com.example.pricetracker.compare.ComparisonService.Evaluation;
import com.example.pricetracker.dto.CheaperItem;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.provider.HtmlPriceProvider;
import com.example.pricetracker.provider.SpecProviderRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Поиск той же или лучшей конфигурации дешевле: магазины + демо-фолбэк. */
@Service
public class CheapSearchService {

    private final StoreRegistry stores;
    private final StoreOffersSource offersSource;
    private final DemoCheaperSource demoSource;
    private final ConfigExtractor configExtractor;
    private final QueryExtractor queryExtractor;
    private final SameConfigMatcher matcher;
    private final CategoryCatalog catalog;
    private final ComparisonService comparisons;
    private final SpecProviderRegistry specProviders;
    private final HtmlPriceProvider htmlPrice;

    public CheapSearchService(StoreRegistry stores,
                              StoreOffersSource offersSource,
                              DemoCheaperSource demoSource,
                              ConfigExtractor configExtractor,
                              QueryExtractor queryExtractor,
                              SameConfigMatcher matcher,
                              CategoryCatalog catalog,
                              ComparisonService comparisons,
                              SpecProviderRegistry specProviders,
                              HtmlPriceProvider htmlPrice) {
        this.stores = stores;
        this.offersSource = offersSource;
        this.demoSource = demoSource;
        this.configExtractor = configExtractor;
        this.queryExtractor = queryExtractor;
        this.matcher = matcher;
        this.catalog = catalog;
        this.comparisons = comparisons;
        this.specProviders = specProviders;
        this.htmlPrice = htmlPrice;
    }

    public CheaperResponse find(CheaperRequest request) {
        String category = request.categoryOrPc();
        if (!catalog.exists(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная категория: " + category);
        }
        String url = validateUrl(request.url());
        int limit = request.limitOrDefault();
        List<String> warnings = new ArrayList<>();

        String query = queryExtractor.extract(url, request.title());
        StoreRegistry.Store store = stores.detect(host(url));
        SourceCard source = resolveSource(url, category, query, request.title(), warnings);

        List<CheaperItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int rejected = 0;
        StoreOffersSource.Result search = offersSource.search(
                url, request.title(), limit, request.crossStore(), warnings);

        int realAdded = 0;
        for (StoreProductParser.Offer offer : search.offers()) {
            // Характеристики берём только из самой карточки: подмена характеристик исходника
            // превратила бы любой дорогой товар в «та же конфигурация».
            Map<String, String> specs = configExtractor.extract(offer.text() + " " + offer.title());
            if (addItem(items, seen, source, specs, category, offer.storeLabel(),
                    offer.title(), offer.url(), offer.price(), false) == 0) {
                realAdded++;
            } else {
                rejected++;
            }
        }
        if (!search.offers().isEmpty() && realAdded == 0) {
            warnings.add("Карточки магазинов не прошли строгую проверку комплектации "
                    + "(на страницах не нашлись характеристики или цена выше исходной).");
        }

        List<DemoCheaperSource.Offer> fallback = demoSource.offers(
                category, source.specs(), source.price(), limit);
        for (DemoCheaperSource.Offer offer : fallback) {
            // Ссылка ведёт на реальный поиск магазина по найденной комплектации — по ней можно
            // перейти и проверить актуальную цену вручную.
            String offerUrl = stores.searchUrl(stores.byKey(offer.storeKey()),
                    specsQuery(offer.specs()), host(url));
            rejected += addItem(items, seen, source, offer.specs(), category, offer.store(),
                    offer.title(), offerUrl, offer.price(), true);
        }
        if (!fallback.isEmpty()) {
            warnings.add("У демо-вариантов цены искусственно занижены: ссылка открывает реальный "
                    + "поиск магазина по этой конфигурации, где нужно проверить текущую цену.");
        }

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не нашлось вариантов с той же или лучшей конфигурацией дешевле "
                            + source.price() + " ₽. Попробуйте другую ссылку или порог цены.");
        }

        items.sort(Comparator.comparingDouble(CheaperItem::valueScore).reversed()
                .thenComparing(CheaperItem::price));
        CheaperItem winner = items.get(0);
        int discount = winner.discountPercent();
        String verdict = String.format(
                "Дешевле и не хуже по комплектации: %s — %s ₽ (на %d%% ниже исходных %s ₽), "
                        + "%.1f баллов за конфигурацию и %.2f балла за 1000 ₽. "
                        + "Подходящих вариантов: %d, отклонено: %d.",
                winner.title(), winner.price(), discount, source.price(), winner.score(),
                winner.valueScore(), items.size(), rejected);

        return new CheaperResponse(url, store.label(), category, catalog.get(category).label(),
                source.title(), source.price(), source.specs(), catalog.get(category).attributes(),
                items, winner.id(), verdict, warnings, search.offers().size(), rejected);
    }

    private String specsQuery(Map<String, String> specs) {
        return "сборка ПК "
                + String.join(" ",
                specs.getOrDefault("cpu", ""),
                specs.getOrDefault("gpu", ""),
                specs.getOrDefault("ram_gb", "") + " ГБ",
                specs.getOrDefault("storage_gb", "") + " ГБ SSD",
                "БП " + specs.getOrDefault("psu_w", "") + " Вт",
                specs.getOrDefault("cooling", ""),
                specs.getOrDefault("os", ""),
                specs.getOrDefault("case", ""))
                .replaceAll("\\s+", " ").trim();
    }

    private int addItem(List<CheaperItem> items, Set<String> seen, SourceCard source,
                        Map<String, String> specs, String category, String store, String title,
                        String url, BigDecimal price, boolean demo) {
        if (items.size() >= 10 || !seen.add(url)) {
            return 1;
        }
        if (!matcher.matches(category, source.specs(), specs) || price.compareTo(source.price()) >= 0) {
            return 1;
        }
        Evaluation evaluation = comparisons.evaluate(category, specs, price);
        UUID id = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        items.add(new CheaperItem(id, store, title, url, price, source.price(), specs,
                evaluation.attributeScores(), evaluation.score(), evaluation.valueScore(),
                evaluation.missingAttributes(), demo));
        return 0;
    }

    private SourceCard resolveSource(String url, String category, String query,
                                     String title, List<String> warnings) {
        String name = title == null || title.isBlank() ? query : title.trim();

        Map<String, String> specs = new LinkedHashMap<>();
        try {
            specs.putAll(specProviders.get("html").fetch(url, category));
        } catch (RuntimeException ex) {
            warnings.add("Характеристики исходной страницы не прочитаны: " + ex.getMessage());
        }
        Map<String, String> fromText = configExtractor.extract(query + " " + name);
        fromText.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                specs.putIfAbsent(key, value);
            }
        });
        if (!complete(category, specs)) {
            Map<String, String> demo = specProviders.get("mock").fetch(url, category);
            demo.forEach(specs::putIfAbsent);
            warnings.add("Не все характеристики удалось прочитать со страницы — недостающие "
                    + "взяты из демо-профиля ссылки.");
        }

        BigDecimal price;
        try {
            price = htmlPrice.fetch(url).price();
        } catch (RuntimeException ex) {
            double score = comparisons.evaluate(category, specs, BigDecimal.ONE).score();
            double estimated = Math.max(60000, Math.min(450000, 130000.0 * score / 60.0));
            price = BigDecimal.valueOf(Math.round(estimated / 100) * 100)
                    .setScale(2, RoundingMode.HALF_UP);
            warnings.add("Цена исходной страницы недоступна (" + ex.getMessage()
                    + ") — оценена по комплектации.");
        }
        return new SourceCard(name, price, specs);
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

    private boolean complete(String category, Map<String, String> specs) {
        return catalog.get(category).attributes().stream()
                .allMatch(attribute -> specs.containsKey(attribute.key())
                        && !specs.get(attribute.key()).isBlank());
    }

    private String host(String url) {
        return URI.create(url).getHost();
    }

    private record SourceCard(String title, BigDecimal price, Map<String, String> specs) {
    }
}
