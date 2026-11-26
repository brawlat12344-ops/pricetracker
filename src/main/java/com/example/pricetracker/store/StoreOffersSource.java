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
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public StoreOffersSource(StoreRegistry stores, QueryExtractor queries, StoreProductParser parser) {
        this.stores = stores;
        this.queries = queries;
        this.parser = parser;
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
        List<StoreProductParser.Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StoreRegistry.Store primary = stores.byKey("dns");
        searchStore(primary, query, "", limit, offers, seen, warnings);
        if (offers.isEmpty()) {
            searchStore(stores.byKey("generic"), query, "", limit, offers, seen, warnings);
        }
        return new Result(primary, query, offers);
    }

    private void searchStore(StoreRegistry.Store store, String query, String host, int limit,
                             List<StoreProductParser.Offer> offers, Set<String> seen,
                             List<String> warnings) {
        try {
            String html = fetch(stores.searchUrl(store, query, host));
            List<StoreProductParser.Offer> parsed = parser.parse(html, store.key(), store.label(), limit);
            int added = 0;
            for (StoreProductParser.Offer offer : parsed) {
                if (seen.add(offer.url()) && offers.size() < limit * 2) {
                    offers.add(offer);
                    added++;
                }
            }
            if (added == 0) {
                warnings.add(store.label() + ": карточки с ценой не найдены (сайт может отдавать пустую оболочку SPA).");
            }
        } catch (RuntimeException ex) {
            warnings.add(store.label() + ": поиск недоступен — " + ex.getMessage());
        }
    }

    private String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 (compatible; PriceTrackerEdu/1.0; +https://example.local/bot)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                throw new IllegalStateException("страница проверки бота (HTTP 202)");
            }
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("сетевая ошибка: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("запрос прерван", e);
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
