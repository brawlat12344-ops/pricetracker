package com.example.pricetracker.provider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real provider: JSON-LD Product.price first, meta tags fallback. */
@Component
public class HtmlPriceProvider implements PriceProvider {

    private static final Pattern PRICE = Pattern.compile("\"(?:price|lowPrice)\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern META = Pattern.compile(
            "<meta[^>]+(?:property=\"product:price:amount\"|itemprop=\"price\"|name=\"price\")[^>]+content=\"([0-9]+(?:\\.[0-9]+)?)\"",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() {
        return "html";
    }

    @Override
    public PriceResult fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "PriceTrackerEdu/1.0 (+https://example.local/bot)")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new PriceProvider.ProviderException("HTTP " + response.statusCode() + " for " + url);
            }
            Document doc = Jsoup.parse(response.body());

            for (org.jsoup.nodes.Element script : doc.select("script[type=application/ld+json]")) {
                Matcher matcher = PRICE.matcher(script.data());
                if (matcher.find()) {
                    return PriceResult.of(parse(matcher.group(1)));
                }
            }
            Matcher meta = META.matcher(response.body());
            if (meta.find()) {
                return PriceResult.of(parse(meta.group(1)));
            }
            throw new PriceProvider.ProviderException("No structured price found on " + url);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PriceProvider.ProviderException("Failed to fetch " + url, e);
        }
    }

    private BigDecimal parse(String raw) {
        return new BigDecimal(raw.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
    }
}
