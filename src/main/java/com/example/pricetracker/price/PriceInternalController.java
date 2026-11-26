package com.example.pricetracker.price;

import com.example.pricetracker.catalog.PriceClient;
import com.example.pricetracker.store.StoreOffersSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Внутренний API price-сервиса: поиск карточек магазинов по короткому или исходному запросу. */
@RestController
@RequestMapping("/internal")
public class PriceInternalController {

    private final StoreOffersSource offers;

    public PriceInternalController(StoreOffersSource offers) {
        this.offers = offers;
    }

    @PostMapping("/offers")
    public PriceClient.OffersResponse search(@RequestBody PriceClient.OffersRequest request) {
        List<String> warnings = new ArrayList<>();
        StoreOffersSource.Result result;
        if (request.query() != null && !request.query().isBlank()) {
            result = offers.searchQuery(request.query(), request.limit(), warnings,
                    Duration.ofMillis(Math.max(500, request.timeoutMs())));
        } else {
            result = offers.search(request.sourceUrl(), request.title(), request.limit(),
                    request.crossStore(), warnings);
        }
        return new PriceClient.OffersResponse(result.offers(), warnings, result.query(),
                result.sourceStore().key());
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("service", "price", "status", "UP");
    }
}
