package com.example.pricetracker.catalog;

import com.example.pricetracker.store.StoreProductParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** HTTP-клиент catalog → price: поиск карточек и цен в магазинах. */
@Component
public class PriceClient {

    public record OffersRequest(String query, String sourceUrl, String title, int limit,
                                boolean crossStore, long timeoutMs) {
    }

    public record OffersResponse(List<StoreProductParser.Offer> offers, List<String> warnings,
                                 String query, String sourceStoreKey) {
    }

    private final RestClient client;

    public PriceClient(@Value("${services.price.url:http://price:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(40));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public OffersResponse search(OffersRequest request) {
        OffersResponse body = client.post()
                .uri("/internal/offers")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(request)
                .retrieve()
                .body(OffersResponse.class);
        return body == null
                ? new OffersResponse(List.of(), List.of("price-сервис вернул пустой ответ"), request.query(), "generic")
                : body;
    }

    public Map<String, Object> info() {
        Map<String, Object> body = client.get()
                .uri("/internal/info")
                .retrieve()
                .body(Map.class);
        return body == null ? Map.of("status", "unavailable") : body;
    }
}
