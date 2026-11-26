package com.example.pricetracker.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Внутренний API отдаёт JSON-ошибки, а не Whitelabel HTML.
 * Тогда gateway получает понятный message и показывает его пользователю.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid_request", "message", ex.getMessage()));
    }
}
