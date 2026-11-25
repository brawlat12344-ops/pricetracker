package com.example.pricetracker.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Универсальный парсер карточек: JSON-LD, meta, DOM-карточки и результаты Bing. */
@Component
public class StoreProductParser {

    public record Offer(String storeKey, String storeLabel, String title,
                        String url, BigDecimal price, String text) {
    }

    private static final Pattern PRICE = Pattern.compile(
            "(?i)(?:price|цена|₽|руб)[^0-9]{0,12}([0-9][0-9\\s\\u00a0]{2,}(?:[.,][0-9]{1,2})?)");
    private static final Pattern ANY_PRICE = Pattern.compile("([0-9][0-9\\s\\u00a0]{3,}(?:[.,][0-9]{1,2})?)");

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Offer> parse(String html, String storeKey, String storeLabel, int limit) {
        Document doc = Jsoup.parse(html);
        List<Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode node : jsonLdProducts(doc)) {
            String name = node.path("name").asText("");
            String url = node.path("url").asText("");
            BigDecimal price = jsonPrice(node);
            add(offers, seen, storeKey, storeLabel, name, url, price, name, limit);
            if (offers.size() >= limit) {
                return offers;
            }
        }

        for (Element card : doc.select("div[class*=product-card],div[class*=productCard],li[class*=product],article")) {
            Element link = card.selectFirst("a[href]");
            if (link == null) {
                continue;
            }
            String name = link.attr("title");
            if (name.isBlank()) {
                Element heading = card.selectFirst("h2,h3,h4");
                name = heading == null ? link.text() : heading.text();
            }
            String text = card.text();
            BigDecimal price = price(text);
            add(offers, seen, storeKey, storeLabel, name, link.attr("abs:href"), price, text, limit);
            if (offers.size() >= limit) {
                return offers;
            }
        }

        for (Element link : doc.select("li.b_algo h2 a[href]")) {
            String url = decodeBing(link.attr("abs:href"));
            Element container = link.closest("li.b_algo");
            String text = container == null ? link.text() : container.text();
            BigDecimal price = price(text);
            add(offers, seen, storeKey, storeLabel, link.text(), url, price, text, limit);
            if (offers.size() >= limit) {
                break;
            }
        }
        return offers;
    }

    private List<JsonNode> jsonLdProducts(Document doc) {
        List<JsonNode> result = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                collectProducts(mapper.readTree(script.data()), result);
            } catch (Exception ignored) {
                // битый JSON-LD пропускаем
            }
        }
        return result;
    }

    private void collectProducts(JsonNode node, List<JsonNode> out) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectProducts(child, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String type = node.path("@type").asText("");
        if (type.equalsIgnoreCase("Product") || node.has("offers")) {
            out.add(node);
        }
        node.fields().forEachRemaining(entry -> collectProducts(entry.getValue(), out));
    }

    private BigDecimal jsonPrice(JsonNode node) {
        JsonNode offers = node.get("offers");
        JsonNode priceNode = null;
        if (offers != null) {
            priceNode = offers.isArray() && offers.size() > 0
                    ? offers.get(0).get("price")
                    : offers.get("price");
            if (priceNode == null) {
                priceNode = offers.findValue("price");
            }
        }
        if (priceNode == null) {
            priceNode = node.get("price");
        }
        return priceNode == null ? null : parsePrice(priceNode.asText());
    }

    private BigDecimal price(String text) {
        Matcher matcher = PRICE.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            matcher = ANY_PRICE.matcher(text == null ? "" : text);
        }
        return matcher.find() ? parsePrice(matcher.group(1)) : null;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            String normalized = raw.replace("\u00a0", "").replace(" ", "")
                    .replace(',', '.').replaceAll("[^0-9.]", "");
            if (normalized.isEmpty()) {
                return null;
            }
            BigDecimal value = new BigDecimal(normalized);
            return value.compareTo(BigDecimal.valueOf(1000)) >= 0
                    && value.compareTo(BigDecimal.valueOf(10_000_000)) <= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean add(List<Offer> offers, Set<String> seen, String storeKey, String storeLabel,
                        String title, String url, BigDecimal price, String text, int limit) {
        if (title == null || title.isBlank() || price == null || !validUrl(url) || !seen.add(url)) {
            return false;
        }
        if (offers.size() >= limit) {
            return false;
        }
        offers.add(new Offer(storeKey, storeLabel, title.trim(), url, price, text));
        return true;
    }

    private boolean validUrl(String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return false;
        }
        try {
            return URI.create(url).getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

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
}
