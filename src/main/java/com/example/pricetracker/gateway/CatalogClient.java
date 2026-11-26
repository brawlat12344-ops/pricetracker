package com.example.pricetracker.gateway;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.dto.LaptopRequest;
import com.example.pricetracker.dto.LaptopResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP-клиент gateway → catalog. Все пользовательские отчёты приходят по сети,
 * а не из локальных бинов — это и есть межсервисное взаимодействие.
 */
@Component
public class CatalogClient {

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CatalogClient(@Value("${services.catalog.url:http://catalog:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public List<CategoryCatalog.CategoryDef> categories() {
        List<CategoryCatalog.CategoryDef> body = client.get()
                .uri("/internal/categories")
                .retrieve()
                .onStatus(this::isError, this::handleError)
                .body(new ParameterizedTypeReference<>() {
                });
        return body == null ? List.of() : body;
    }

    public CheaperResponse cheaper(CheaperRequest request) {
        return client.post()
                .uri("/internal/cheaper")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(request)
                .retrieve()
                .onStatus(this::isError, this::handleError)
                .body(CheaperResponse.class);
    }

    public BuildResponse build(BuildRequest request) {
        return client.post()
                .uri("/internal/build")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(request)
                .retrieve()
                .onStatus(this::isError, this::handleError)
                .body(BuildResponse.class);
    }

    public LaptopResponse laptops(LaptopRequest request) {
        return client.post()
                .uri("/internal/laptops")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(request)
                .retrieve()
                .onStatus(this::isError, this::handleError)
                .body(LaptopResponse.class);
    }

    /** Проверка цепочки gateway → catalog → price для smoke-тестов и демонстрации. */
    public Map<String, Object> info() {
        Map<String, Object> body = client.get()
                .uri("/internal/info")
                .retrieve()
                .onStatus(this::isError, this::handleError)
                .body(new ParameterizedTypeReference<>() {
                });
        return body == null ? Map.of("status", "unavailable") : body;
    }

    private boolean isError(HttpStatusCode status) {
        return status.is4xxClientError() || status.is5xxServerError();
    }

    /** Достаёт message из JSON-ошибки catalog, чтобы в UI не попадал Whitelabel HTML. */
    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String message = null;
        try {
            JsonNode node = mapper.readTree(body);
            if (node.hasNonNull("message")) {
                message = node.get("message").asText();
            } else if (node.hasNonNull("error")) {
                message = node.get("error").asText();
            }
        } catch (Exception ignored) {
            // Ответ не JSON — используем статус.
        }
        if (message == null || message.isBlank()) {
            message = "catalog вернул HTTP " + response.getStatusCode().value();
        }
        throw new CatalogClientException(message);
    }
}
