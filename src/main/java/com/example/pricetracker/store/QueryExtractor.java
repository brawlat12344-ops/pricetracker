package com.example.pricetracker.store;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Достаёт поисковую фразу из вставленной ссылки или использует введённое название. */
@Component
public class QueryExtractor {

    public String extract(String url, String title) {
        if (title != null && !title.isBlank()) {
            return clean(title, 120);
        }
        try {
            URI uri = URI.create(url.trim());
            String raw = uri.getRawQuery();
            if (raw != null) {
                for (String pair : raw.split("&")) {
                    String[] kv = pair.split("=", 2);
                    String key = kv[0].toLowerCase(Locale.ROOT);
                    if (kv.length == 2 && (key.equals("q") || key.equals("text") || key.equals("query") || key.equals("search"))) {
                        return clean(URLDecoder.decode(kv[1], StandardCharsets.UTF_8), 120);
                    }
                }
            }
            String path = uri.getPath();
            if (path != null) {
                String[] segments = path.split("/");
                for (int i = segments.length - 1; i >= 0; i--) {
                    String segment = segments[i].trim();
                    if (!segment.isEmpty() && !segment.matches("(?i)\\d+")) {
                        String value = clean(URLDecoder.decode(segment, StandardCharsets.UTF_8), 120);
                        if (value.length() >= 4) {
                            return value;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // На всякий случай возвращаем фразу по умолчанию.
        }
        return "сборка ПК gametech";
    }

    private String clean(String raw, int maxLength) {
        String value = raw.trim()
                .replaceAll("(?i)[-_]+", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength).trim();
        }
        return value.isEmpty() ? "сборка ПК gametech" : value;
    }
}
