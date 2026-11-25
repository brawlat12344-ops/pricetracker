package com.example.pricetracker.store;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Описания магазинов и шаблоны их поисковых URL. Добавить магазин — одна строка. */
@Component
public class StoreRegistry {

    public record Store(String key, String label, List<String> hosts,
                        String searchTemplate, boolean directSearch) {
    }

    private static final Store GENERIC = new Store(
            "generic", "Другой магазин", List.of(),
            "https://www.bing.com/search?q=site%3A{host}+{q}", false);

    private static final List<Store> STORES = List.of(
            new Store("dns", "DNS-Shop", List.of("dns-shop.ru", "www.dns-shop.ru", "shop.dns-shop.ru"),
                    "https://www.dns-shop.ru/search/?q={q}", true),
            new Store("mvideo", "М.Видео", List.of("mvideo.ru", "www.mvideo.ru", "m.video"),
                    "https://www.mvideo.ru/products/search?q={q}", true),
            new Store("citilink", "Ситилинк", List.of("citilink.ru", "www.citilink.ru"),
                    "https://www.citilink.ru/search/?text={q}", true),
            new Store("ozon", "Ozon", List.of("ozon.ru", "www.ozon.ru"),
                    "https://www.ozon.ru/search/?text={q}", true),
            new Store("wildberries", "Wildberries", List.of("wildberries.ru", "www.wildberries.ru"),
                    "https://www.wildberries.ru/catalog/0/search.aspx?search={q}", true),
            GENERIC);

    public List<Store> all() {
        return STORES;
    }

    public Store detect(String host) {
        if (host != null) {
            String normalized = host.toLowerCase(Locale.ROOT);
            for (Store store : STORES) {
                if (store.hosts().stream().anyMatch(normalized::equals)) {
                    return store;
                }
            }
        }
        return GENERIC;
    }

    public Store byKey(String key) {
        return STORES.stream()
                .filter(store -> store.key().equalsIgnoreCase(key == null ? "" : key))
                .findFirst()
                .orElse(GENERIC);
    }

    public String searchUrl(Store store, String query, String host) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String encodedHost = URLEncoder.encode(host == null ? "" : host, StandardCharsets.UTF_8);
        return store.searchTemplate().replace("{q}", encodedQuery).replace("{host}", encodedHost);
    }
}
