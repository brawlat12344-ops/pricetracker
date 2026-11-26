package com.example.pricetracker.catalog;

import com.example.pricetracker.store.StoreOffersSource;
import com.example.pricetracker.store.StoreRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

/**
 * Подменяет локальный парсер в catalog на сетевой вызов price-сервиса.
 * @Primary важен: локальный бин остаётся в контексте только как fallback-реализация.
 */
@Configuration
public class RemoteStoreOffers {

    @Bean
    @Primary
    public StoreOffersSource remoteStoreOffersSource(PriceClient price, StoreRegistry stores) {
        return new StoreOffersSource(null, null, null, null) {
            @Override
            public Result search(String sourceUrl, String title, int limit, boolean crossStore,
                                 List<String> warnings) {
                PriceClient.OffersResponse response = price.search(new PriceClient.OffersRequest(
                        null, sourceUrl, title, limit, crossStore, 12_000));
                warnings.addAll(response.warnings());
                return new Result(stores.byKey(response.sourceStoreKey()), response.query(), response.offers());
            }

            @Override
            public Result searchQuery(String query, int limit, List<String> warnings) {
                return query(query, limit, warnings, 12_000);
            }

            @Override
            public Result searchQuery(String query, int limit, List<String> warnings, Duration timeout) {
                return query(query, limit, warnings, timeout.toMillis());
            }

            private Result query(String query, int limit, List<String> warnings, long timeoutMs) {
                PriceClient.OffersResponse response = price.search(new PriceClient.OffersRequest(
                        query, null, null, limit, false, timeoutMs));
                warnings.addAll(response.warnings());
                return new Result(stores.byKey(response.sourceStoreKey()), response.query(), response.offers());
            }
        };
    }
}
