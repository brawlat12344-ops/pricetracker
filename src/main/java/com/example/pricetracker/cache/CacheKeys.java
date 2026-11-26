package com.example.pricetracker.cache;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** Нормализация ключей кэша: один и тот же запрос в разном регистре даёт один ключ. */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String text(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** URL без фрагмента, с нижним регистром хоста и отсортированными параметрами. */
    public static String url(String raw) {
        String value = text(raw);
        if (value.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) {
                return value;
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String authority = host + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getQuery() == null ? "" : Arrays.stream(uri.getQuery().split("&"))
                    .filter(part -> !part.isBlank())
                    .sorted()
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
            return scheme + "://" + authority + path + (query.isEmpty() ? "" : "?" + query);
        } catch (RuntimeException ex) {
            return value;
        }
    }

    public static String key(String kind, Object... parts) {
        StringBuilder result = new StringBuilder(kind);
        for (Object part : parts) {
            result.append('|').append(text(String.valueOf(part)));
        }
        return result.toString();
    }
}
