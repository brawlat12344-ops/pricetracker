package com.example.pricetracker.provider;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SpecProviderRegistry {

    private final Map<String, SpecProvider> providers;

    public SpecProviderRegistry(java.util.List<SpecProvider> beans) {
        this.providers = beans.stream()
                .collect(Collectors.toMap(SpecProvider::name, Function.identity()));
    }

    public SpecProvider get(String name) {
        SpecProvider provider = providers.get(name);
        if (provider == null) {
            throw new SpecProvider.ProviderException("Unknown spec provider: " + name);
        }
        return provider;
    }
}
