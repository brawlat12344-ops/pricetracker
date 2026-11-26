package com.example.pricetracker.builder;

import com.example.pricetracker.dto.BuildResponse.ComponentOffer;
import com.example.pricetracker.store.StoreOffersSource;
import com.example.pricetracker.store.StoreProductParser;
import com.example.pricetracker.store.StoreRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Ищет 2–3 проверяемых предложения по каждому компоненту.
 * Если сайт блокирует парсер или найдена посторонняя карточка, добавляются
 * явно помеченные демо-предложения со ссылкой на реальный поиск.
 */
@Component
public class PartPriceService {

    /** Один компонент сборки: поисковый запрос и ориентир цены из каталога. */
    public record ComponentQuery(String query, BigDecimal fallback) {
    }

    private record BatchResult(String query, List<ComponentOffer> offers, List<String> warnings) {
    }

    private static final Set<String> GENERIC_WORDS = Set.of(
            "сборка", "ноутбук", "пк", "computer", "laptop", "gb", "tb", "mah", "kg", "w",
            "watts", "бп", "ocz", "ssd", "nvme", "ddr4", "ddr5", "home", "pro",
            "купить", "цена", "магазин", "наличии", "москва", "россия");

    private final StoreOffersSource offers;
    private final StoreRegistry stores;

    public PartPriceService(StoreOffersSource offers, StoreRegistry stores) {
        this.offers = offers;
        this.stores = stores;
    }

    /**
     * Все компоненты ищутся параллельно и с короткими таймаутами: иначе 8 последовательных
     * запросов к блокирующим сайтам не укладываются в таймаут nginx.
     */
    public Map<String, List<ComponentOffer>> offersForAll(List<ComponentQuery> specs,
                                                           List<String> warnings) {
        Map<String, List<ComponentOffer>> result = new HashMap<>();
        if (specs.isEmpty()) {
            return result;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, specs.size()));
        try {
            List<Future<BatchResult>> futures = new ArrayList<>();
            for (ComponentQuery spec : specs) {
                futures.add(pool.submit(() -> {
                    List<String> localWarnings = new ArrayList<>();
                    List<ComponentOffer> found = offersFor(spec.query(), spec.fallback(), localWarnings);
                    return new BatchResult(spec.query(), found, localWarnings);
                }));
            }
            for (Future<BatchResult> future : futures) {
                BatchResult batch = future.get();
                result.put(batch.query(), batch.offers());
                // Ограничиваем число warnings: по 1–2 на компонент, чтобы отчёт оставался читаемым.
                batch.warnings().stream().limit(2).forEach(warning -> {
                    if (!warnings.contains(warning)) {
                        warnings.add(warning);
                    }
                });
            }
        } catch (Exception ex) {
            warnings.add("Параллельный поиск цен прерван: " + ex.getMessage());
        } finally {
            pool.shutdown();
        }
        return result;
    }

    /** Реальные карточки + демо-заполнение до трёх предложений. */
    public List<ComponentOffer> offersFor(String query, BigDecimal fallback, List<String> warnings) {
        List<ComponentOffer> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        StoreOffersSource.Result found = offers.searchQuery(query, 4, warnings, Duration.ofSeconds(3));
        found.offers().stream()
                .filter(offer -> credible(offer, query, fallback))
                .sorted(Comparator.comparing(StoreProductParser.Offer::price))
                .forEach(offer -> {
                    ComponentOffer mapped = map(offer, query);
                    if (result.size() < 3 && seen.add(mapped.store() + "|" + mapped.price())) {
                        result.add(mapped);
                    }
                });

        if (result.isEmpty()) {
            warnings.add("По компоненту «" + query + "» проверяемые цены не найдены: "
                    + "добавлены демо-предложения. Откройте ссылку и уточните цену и наличие.");
        }
        for (ComponentOffer demo : demoOffers(query, fallback)) {
            if (result.size() >= 3) {
                break;
            }
            if (seen.add(demo.store() + "|" + demo.price())) {
                result.add(demo);
            }
        }
        return result;
    }

    /** Демо-карточки: расчёт по каталогу, а не коммерческое предложение. */
    public List<ComponentOffer> demoOffers(String query, BigDecimal fallback) {
        String[] labels = {"Демо-каталог", "Демо-М.Видео", "Демо-DNS"};
        double[] factors = {1.00, 1.06, 0.96};
        String url = stores.searchUrl(stores.byKey("dns"), query, "");
        List<ComponentOffer> result = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            BigDecimal price = fallback.multiply(BigDecimal.valueOf(factors[i]))
                    .setScale(0, RoundingMode.HALF_UP);
            result.add(new ComponentOffer(labels[i], price, url,
                    "Наличие не указано — проверить", true, false,
                    "Расчётная цена каталога, не коммерческое предложение"));
        }
        return result;
    }

    private ComponentOffer map(StoreProductParser.Offer offer, String query) {
        String text = (offer.title() + " " + offer.text()).toLowerCase(Locale.ROOT);
        String availability = text.contains("нет в наличии")
                ? "Нет в наличии — проверить"
                : text.contains("в наличии")
                ? "В наличии — проверить"
                : "Наличие не указано — проверить";
        boolean promo = text.contains("скидка") || text.contains("акция")
                || text.contains("распродажа") || text.contains("promo");
        String note = promo
                ? "Признаки акции/скидки — уточните итоговую цену на сайте"
                : "Цена и наличие должны быть проверены по ссылке";
        String url = offer.url() == null || offer.url().isBlank()
                ? stores.searchUrl(stores.byKey("dns"), query, "")
                : offer.url();
        return new ComponentOffer(offer.storeLabel(), offer.price(), url,
                availability, false, promo, note);
    }

    /** Защита от случайных цен из сниппетов: цена и карточка должны соответствовать детали. */
    private boolean credible(StoreProductParser.Offer offer, String query, BigDecimal fallback) {
        if (offer.price() == null || offer.price().signum() <= 0 || fallback.signum() <= 0) {
            return false;
        }
        BigDecimal floor = fallback.multiply(BigDecimal.valueOf(0.55));
        BigDecimal ceiling = fallback.multiply(BigDecimal.valueOf(1.60));
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
        if (matches < 2) {
            return false;
        }
        // Для точных моделей артикул/цифра в названии обязательны: иначе цена другой
        // конфигурации той же серии выглядит «похожей» и завышает результат.
        String exact = tokens.stream()
                .filter(token -> token.length() >= 4 && token.matches(".*[0-9].*"))
                .findFirst().orElse(null);
        return exact == null || title.contains(exact);
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
