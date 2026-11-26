package com.example.pricetracker.store;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Реальный поиск карточек в магазинах: по тому же сайту и, опционально, по другим. */
@Component
public class StoreOffersSource {

    public record Result(StoreRegistry.Store sourceStore, String query,
                         List<StoreProductParser.Offer> offers) {
    }

    private final StoreRegistry stores;
    private final QueryExtractor queries;
    private final StoreProductParser parser;
    private final StoreBreaker breaker;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** Короткие таймауты для конфигуратора: много компонентов в одном запросе. */
    private final HttpClient fastClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public StoreOffersSource(StoreRegistry stores, QueryExtractor queries,
                             StoreProductParser parser, StoreBreaker breaker) {
        this.stores = stores;
        this.queries = queries;
        this.parser = parser;
        this.breaker = breaker;
    }

    public Result search(String sourceUrl, String title, int limit, boolean crossStore,
                         List<String> warnings) {
        String host = host(sourceUrl);
        StoreRegistry.Store sourceStore = stores.detect(host);
        String query = queries.extract(sourceUrl, title);

        List<StoreProductParser.Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        searchStore(sourceStore, query, host, limit, offers, seen, warnings);
        if (crossStore) {
            for (StoreRegistry.Store store : stores.all()) {
                if (!store.equals(sourceStore) && store.directSearch()) {
                    searchStore(store, query, host, limit, offers, seen, warnings);
                }
            }
            searchStore(stores.byKey("generic"), query, host, limit, offers, seen, warnings);
        }
        return new Result(sourceStore, query, offers);
    }

    /** Поиск по готовому текстовому запросу — используется конфигуратором. */
    public Result searchQuery(String query, int limit, List<String> warnings) {
        return searchQuery(query, limit, warnings, Duration.ofSeconds(12), client);
    }

    /**
     * Быстрый поиск для конфигуратора: сколько угодно компонентов в одном запросе,
     * но с короткими таймаутами, чтобы ответ успевал вернуться через nginx.
     */
    public Result searchQuery(String query, int limit, List<String> warnings, Duration timeout) {
        return searchQuery(query, limit, warnings, timeout, fastClient);
    }

    private Result searchQuery(String query, int limit, List<String> warnings,
                               Duration timeout, HttpClient http) {
        List<StoreProductParser.Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StoreRegistry.Store primary = stores.byKey("dns");
        searchStore(primary, query, "", limit, offers, seen, warnings, http, timeout);
        if (offers.isEmpty()) {
            searchStore(stores.byKey("generic"), query, "", limit, offers, seen, warnings, http, timeout);
        }
        return new Result(primary, query, offers);
    }

    private void searchStore(StoreRegistry.Store store, String query, String host, int limit,
                             List<StoreProductParser.Offer> offers, Set<String> seen,
                             List<String> warnings) {
        searchStore(store, query, host, limit, offers, seen, warnings, client, Duration.ofSeconds(12));
    }

    private void searchStore(StoreRegistry.Store store, String query, String host, int limit,
                             List<StoreProductParser.Offer> offers, Set<String> seen,
                             List<String> warnings, HttpClient http, Duration timeout) {
        if (!breaker.allow(store.key())) {
            warnings.add(store.label() + ": временно пропущен — цепь разомкнута после повторных блокировок.");
            return;
        }
        long started = System.nanoTime();
        try {
            String html = fetch(stores.searchUrl(store, query, host), http, timeout);
            breaker.latency(store.key(), started);
            List<StoreProductParser.Offer> parsed = parser.parse(html, store.key(), store.label(), limit);
            int added = 0;
            for (StoreProductParser.Offer offer : parsed) {
                if (seen.add(offer.url()) && offers.size() < limit * 2) {
                    offers.add(offer);
                    added++;
                }
            }
            if (added == 0) {
                breaker.failure(store.key(), "empty");
                warnings.add(store.label() + ": карточки с ценой не найдены (сайт может отдавать пустую оболочку SPA).");
            } else {
                breaker.success(store.key());
            }
        } catch (StoreFetchException ex) {
            breaker.latency(store.key(), started);
            breaker.failure(store.key(), ex.status() == 0 ? "error" : "blocked");
            warnings.add(store.label() + ": поиск недоступен — " + ex.getMessage());
        } catch (RuntimeException ex) {
            breaker.latency(store.key(), started);
            breaker.failure(store.key(), "error");
            warnings.add(store.label() + ": поиск недоступен — " + ex.getMessage());
        }
    }

    private String fetch(String url, HttpClient http, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "Mozilla/5.0 (compatible; PriceTrackerEdu/1.0; +https://example.local/bot)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 202) {
                throw new StoreFetchException(status, "страница проверки бота (HTTP 202)");
            }
            if (status / 100 != 2) {
                throw new StoreFetchException(status, "HTTP " + status);
            }
            return response.body();
        } catch (IOException e) {
            throw new StoreFetchException(0, "сетевая ошибка: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoreFetchException(0, "запрос прерван");
        }
    }

    /** Код ответа нужен breaker'у, чтобы отличить блокировку от сетевой ошибки. */
    private static final class StoreFetchException extends RuntimeException {
        private final int status;

        private StoreFetchException(int status, String message) {
            super(message);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }

    private String host(String url) {
        try {
            return URI.create(url.trim()).getHost();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
