package com.example.pricetracker.gateway;

/** Ошибка, пришедшая от catalog в читаемом виде (без HTML-страницы Spring Boot). */
public class CatalogClientException extends RuntimeException {

    public CatalogClientException(String message) {
        super(message);
    }
}
