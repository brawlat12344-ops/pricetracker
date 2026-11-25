package com.example.pricetracker.provider;

public interface PriceProvider {

    String name();

    PriceResult fetch(String url);

    class ProviderException extends RuntimeException {
        public ProviderException(String message) {
            super(message);
        }

        public ProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
