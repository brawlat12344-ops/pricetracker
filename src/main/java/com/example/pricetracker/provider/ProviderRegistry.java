package com.example.pricetracker.provider;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderRegistry {

    private final Map<String, PriceProvider> providers;

    public ProviderRegistry(java.util.List<PriceProvider> beans) {
        this.providers = beans.stream()
                .collect(Collectors.toMap(PriceProvider::name, Function.identity()));
    }

    public PriceProvider get(String name) {
        PriceProvider provider = providers.get(name);
        if (provider == null) {
            throw new PriceProvider.ProviderException("Unknown price provider: " + name);
        }
        return provider;
    }
}
