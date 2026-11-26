package com.example.pricetracker.builder;

import com.example.pricetracker.store.StoreOffersSource;
import com.example.pricetracker.store.StoreProductParser;
import com.example.pricetracker.store.StoreRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ищет цену на всю собранную конфигурацию одним запросом.
 * Если сайт блокирует парсер или найдена явно посторонняя цена, возвращается демо-цена.
 */
@Component
public class PartPriceService {

    public record Price(BigDecimal value, String store, String url, boolean demo) {
    }

    public record Result(Price price, List<String> warnings) {
    }

    private static final Set<String> GENERIC_WORDS = Set.of(
            "сборка", "ноутбук", "пк", "computer", "laptop", "gb", "tb", "mah", "kg", "w",
            "watts", "бп", "ocz", "ssd", "nvme", "ddr4", "ddr5", "home", "pro");

    private final StoreOffersSource offers;
    private final StoreRegistry stores;

    public PartPriceService(StoreOffersSource offers, StoreRegistry stores) {
        this.offers = offers;
        this.stores = stores;
    }

    public Result price(String query, BigDecimal fallback) {
        List<String> warnings = new ArrayList<>();
        StoreOffersSource.Result result = offers.searchQuery(query, 3, warnings);
        StoreProductParser.Offer best = result.offers().stream()
                .filter(offer -> credible(offer, query, fallback))
                .min(Comparator.comparing(StoreProductParser.Offer::price))
                .orElse(null);

        if (best != null) {
            return new Result(new Price(best.price(), best.storeLabel(), best.url(), false), warnings);
        }

        warnings.add("Реальную цену по сборке получить не удалось: показан расчёт по демо-каталогу. "
                + "Откройте ссылку и проверьте текущую цену в магазине.");
        String searchUrl = stores.searchUrl(stores.byKey("dns"), query, "");
        return new Result(new Price(fallback, "Демо-каталог", searchUrl, true), warnings);
    }

    /** Защита от случайных цен из snippets: цена и карточка должны соответствовать сборке. */
    private boolean credible(StoreProductParser.Offer offer, String query, BigDecimal fallback) {
        if (offer.price() == null || offer.price().signum() <= 0 || fallback.signum() <= 0) {
            return false;
        }
        BigDecimal floor = fallback.multiply(BigDecimal.valueOf(0.4));
        BigDecimal ceiling = fallback.multiply(BigDecimal.valueOf(2.5));
        if (offer.price().compareTo(floor) < 0 || offer.price().compareTo(ceiling) > 0) {
            return false;
        }
        String title = (offer.title() + " " + offer.text()).toLowerCase(Locale.ROOT);
        List<String> tokens = distinctTokens(query);
        int matches = 0;
        for (String token : tokens) {
            if (title.contains(token)) {
                matches++;
            }
        }
        return matches >= 2;
    }

    private List<String> distinctTokens(String query) {
        List<String> tokens = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^a-z0-9а-яё]+")) {
            if (raw.length() < 3 || GENERIC_WORDS.contains(raw) || !seen.add(raw)) {
                continue;
            }
            tokens.add(raw);
        }
        return tokens;
    }
}
