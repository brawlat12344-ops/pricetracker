package com.example.pricetracker.analog;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Реальный поиск аналогов. DuckDuckGo HTML — основной источник, Bing — запасной
 * (DuckDuckGo часто отдаёт страницу проверки бота с HTTP 202).
 * Источник возвращает только ссылки: цена и характеристики дозагружаются
 * html-провайдерами с самих страниц. Если поиск заблокирован, сервис показывает
 * предупреждение и демо-каталог.
 */
@Component
public class WebSearchSource implements AnalogSource {

    private static final String DUCKDUCKGO = "https://html.duckduckgo.com/html/?q=";
    private static final String BING = "https://www.bing.com/search?q=";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() {
        return "web";
    }

    @Override
    public List<Candidate> find(String sourceUrl, String category, String title, int limit) {
        String query = query(category, title);
        RuntimeException lastError = null;

        for (String engine : List.of("duckduckgo", "bing")) {
            try {
                String html = fetch((engine.equals("bing") ? BING : DUCKDUCKGO)
                        + URLEncoder.encode(query, StandardCharsets.UTF_8), engine);
                List<Candidate> found = engine.equals("bing")
                        ? parseBing(html, sourceUrl, limit)
                        : parseDuckDuckGo(html, sourceUrl, limit);
                if (!found.isEmpty()) {
                    return found;
                }
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }
        throw new SourceException("Поиск не вернул ссылок (DuckDuckGo, Bing)"
                + (lastError == null ? "" : ": " + lastError.getMessage()), lastError);
    }

    private String fetch(String url, String engine) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 (compatible; PriceTrackerEdu/1.0; +https://example.local/bot)")
                .GET()
                .build();
        String html;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                throw new SourceException(engine + " вернул страницу проверки бота (HTTP 202)");
            }
            if (response.statusCode() / 100 != 2) {
                throw new SourceException(engine + " вернул HTTP " + response.statusCode());
            }
            html = response.body();
        } catch (IOException e) {
            throw new SourceException("Не удалось выполнить поиск в " + engine + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceException("Поиск прерван", e);
        }
        return html;
    }

    private List<Candidate> parseDuckDuckGo(String html, String sourceUrl, int limit) {
        Document doc = Jsoup.parse(html);
        return collect(doc.select("a.result__a"), false, sourceUrl, limit);
    }

    private List<Candidate> parseBing(String html, String sourceUrl, int limit) {
        Document doc = Jsoup.parse(html);
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String sourceKey = DemoCatalogSource.normalize(sourceUrl);

        for (Element link : doc.select("li.b_algo h2 a[href]")) {
            String url = validUrl(decodeBing(link.attr("abs:href")));
            if (url == null || !seen.add(url) || DemoCatalogSource.normalize(url).equals(sourceKey)) {
                continue;
            }
            String name = link.text().trim();
            result.add(Candidate.link(url, name.isEmpty() ? url : name));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /** Bing оборачивает ссылки в /ck/a?...&u=a1<base64-url>; достаём настоящий адрес. */
    private String decodeBing(String href) {
        int marker = href.indexOf("u=a1");
        if (marker < 0) {
            return href;
        }
        String token = href.substring(marker + 4).split("&")[0];
        StringBuilder padded = new StringBuilder(token);
        while (padded.length() % 4 != 0) {
            padded.append('=');
        }
        try {
            return new String(Base64.getUrlDecoder().decode(padded.toString()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Candidate> collect(Iterable<Element> links, boolean directUrl,
                                    String sourceUrl, int limit) {
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String sourceKey = DemoCatalogSource.normalize(sourceUrl);

        for (Element link : links) {
            String url = directUrl ? validUrl(link.attr("abs:href")) : extractUrl(link.attr("abs:href"));
            if (url == null || !seen.add(url) || DemoCatalogSource.normalize(url).equals(sourceKey)) {
                continue;
            }
            String name = link.text().trim();
            result.add(Candidate.link(url, name.isEmpty() ? url : name));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private String query(String category, String title) {
        String subject = title == null || title.isBlank() ? "" : title.trim();
        String topic = switch (category == null ? "pc" : category.toLowerCase(Locale.ROOT)) {
            case "laptop" -> "ноутбук";
            case "phone" -> "смартфон";
            case "headphones" -> "наушники";
            default -> "сборка ПК";
        };
        return (topic + " " + subject + " цена характеристики").replaceAll("\\s+", " ").trim();
    }

    private String extractUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw;
        int marker = value.indexOf("uddg=");
        if (marker >= 0) {
            value = URLDecoder.decode(value.substring(marker + 5).split("&")[0], StandardCharsets.UTF_8);
        }
        return validUrl(value);
    }

    private String validUrl(String value) {
        if (value == null || (!value.startsWith("http://") && !value.startsWith("https://"))) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || host.endsWith("duckduckgo.com") || host.endsWith("bing.com")) {
                return null;
            }
            return value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
