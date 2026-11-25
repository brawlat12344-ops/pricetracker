package com.example.pricetracker.provider;

import java.util.Map;

public interface SpecProvider {

    String name();

    Map<String, String> fetch(String url, String category);

    class ProviderException extends RuntimeException {
        public ProviderException(String message) {
            super(message);
        }

        public ProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
