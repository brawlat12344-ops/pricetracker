package com.example.pricetracker.web;

import com.example.pricetracker.gateway.CatalogClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** REST-фасад возвращает JSON-ошибку вместо стандартной HTML-страницы. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CatalogClientException.class)
    public ResponseEntity<Map<String, String>> handleCatalogError(CatalogClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "catalog", "message", ex.getMessage()));
    }
}
