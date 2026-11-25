package com.example.pricetracker.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Реальный провайдер характеристик: JSON-LD additionalProperty, meta-теги и эвристики по тексту страницы.
 * Сайты могут менять разметку или блокировать ботов — тогда значения просто не находятся.
 */
@Component
public class HtmlSpecProvider implements SpecProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern CPU = Pattern.compile(
            "(?i)\\b(core i[3579](?:-\\d{4,5})?|i[3579]-\\d{4,5}|ryzen [3579](?: \\d{4})?|apple m[123]|snapdragon \\d(?: gen \\d)?|dimensity \\d{4}|exynos \\d{4}|helio \\w+)");
    private static final Pattern GPU = Pattern.compile(
            "(?i)\\b(rtx ?\\d{4}|gtx ?\\d{4}|rx ?\\d{4}(?: xt)?|radeon \\w+|arc \\w+|iris \\w+|integrated graphics|apple m[123] gpu)");
    private static final Pattern RAM = Pattern.compile(
            "(?i)(\\d{1,3}) ?(?:gb|гб)\\b[^.]{0,40}(?:ram|оператив|memory|памят)");
    private static final Pattern STORAGE = Pattern.compile(
            "(?i)(\\d{1,4}) ?(?:gb|tb|гб|тб)\\b[^.]{0,40}(?:ssd|nvme|storage|накопител|диск)");
    private static final Pattern SCREEN = Pattern.compile(
            "(?i)(4k|uhd|oled|amoled|144hz|120hz|90hz|full hd|fhd)");
    private static final Pattern BATTERY_MAH = Pattern.compile("(?i)(\\d{3,5}) ?(?:mah|мА·?ч)");
    private static final Pattern BATTERY_H = Pattern.compile(
            "(?i)(\\d{1,3}) ?(?:h|hours|ч)\\b[^.]{0,30}(?:work|play|battery|работ|воспроизведен)");
    private static final Pattern WEIGHT = Pattern.compile("(?i)(\\d[.,]\\d) ?(?:kg|кг)");
    private static final Pattern CAMERA = Pattern.compile("(?i)(\\d{2,3}) ?(?:mp|мп)");
    private static final Pattern PSU = Pattern.compile(
            "(?i)(\\d{3,4}) ?(?:w|вт)\\b[^.]{0,30}(?:psu|блок питания|power supply)");
    private static final Pattern COOLING = Pattern.compile(
            "(?i)(liquid|aio|water|жидкост|air|воздуш|stock|боксов)");
    private static final Pattern DRIVER = Pattern.compile("(?i)(planar|dynamic|balanced armature)");
    private static final Pattern ANC = Pattern.compile(
            "(?i)(adaptive anc|active noise cancelling|anc|шумоподавлен|no anc)");
    private static final Pattern CONNECTIVITY = Pattern.compile(
            "(?i)(bluetooth ?[0-9.]+|bt ?[0-9.]+|wired|проводн)");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() {
        return "html";
    }

    @Override
    public Map<String, String> fetch(String url, String category) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "PriceTrackerEdu/1.0 (+https://example.local/bot)")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new SpecProvider.ProviderException("HTTP " + response.statusCode() + " for " + url);
            }

            Document doc = Jsoup.parse(response.body());
            Map<String, String> specs = new LinkedHashMap<>();

            fromJsonLd(doc, specs);
            fromMeta(doc, specs);

            String text = doc.text() + " " + response.body();
            find(CPU, text, specs, "cpu");
            find(GPU, text, specs, "gpu");
            find(RAM, text, specs, "ram_gb");
            find(STORAGE, text, specs, "storage_gb");
            find(SCREEN, text, specs, "screen");
            find(BATTERY_MAH, text, specs, "battery_mah");
            find(BATTERY_H, text, specs, "battery_h");
            find(WEIGHT, text, specs, "weight_kg");
            find(CAMERA, text, specs, "camera");
            find(PSU, text, specs, "psu_w");
            find(COOLING, text, specs, "cooling");
            find(DRIVER, text, specs, "driver");
            find(ANC, text, specs, "anc");
            find(CONNECTIVITY, text, specs, "connectivity");

            return specs;
        } catch (IOException e) {
            throw new SpecProvider.ProviderException("Failed to fetch specs from " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpecProvider.ProviderException("Interrupted while fetching specs from " + url, e);
        }
    }

    private void find(Pattern pattern, String text, Map<String, String> out, String key) {
        if (out.containsKey(key)) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
            out.put(key, value.trim().replaceAll("\\s+", " ").toLowerCase());
        }
    }

    private void fromMeta(Document doc, Map<String, String> out) {
        for (Element meta : doc.select("meta")) {
            String raw = meta.attr("name") + " " + meta.attr("property") + " " + meta.attr("itemprop");
            String key = keyForName(raw);
            String content = meta.attr("content");
            if (key != null && !content.isBlank()) {
                out.putIfAbsent(key, content.trim().toLowerCase());
            }
        }
    }

    private void fromJsonLd(Document doc, Map<String, String> out) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                collect(MAPPER.readTree(script.data()), out);
            } catch (Exception ignored) {
                // битый JSON-LD просто пропускаем
            }
        }
    }

    private void collect(JsonNode node, Map<String, String> out) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode props = node.get("additionalProperty");
        if (props != null && props.isArray()) {
            for (JsonNode prop : props) {
                String name = prop.path("name").asText("").toLowerCase();
                String value = prop.path("value").asText("").trim();
                String key = keyForName(name);
                if (key != null && !value.isEmpty()) {
                    out.putIfAbsent(key, value.toLowerCase());
                }
            }
        }

        node.fields().forEachRemaining(entry -> collect(entry.getValue(), out));
    }

    private String keyForName(String raw) {
        String name = raw == null ? "" : raw.toLowerCase();
        if (name.contains("processor") || name.contains("cpu") || name.contains("процессор")) return "cpu";
        if (name.contains("graphics") || name.contains("gpu") || name.contains("видеокарт")) return "gpu";
        if (name.contains("ram") || name.contains("memory") || name.contains("оператив")) return "ram_gb";
        if (name.contains("storage") || name.contains("ssd") || name.contains("накопител")) return "storage_gb";
        if (name.contains("screen") || name.contains("display") || name.contains("экран")) return "screen";
        if (name.contains("battery") && name.contains("h")) return "battery_h";
        if (name.contains("battery") || name.contains("батаре")) return "battery_mah";
        if (name.contains("weight") || name.contains("вес")) return "weight_kg";
        if (name.contains("camera") || name.contains("камер")) return "camera";
        if (name.contains("watt") || name.contains("psu") || name.contains("блок питания")) return "psu_w";
        if (name.contains("cool") || name.contains("охлажд")) return "cooling";
        if (name.contains("driver") || name.contains("излучател")) return "driver";
        if (name.contains("noise") || name.contains("anc") || name.contains("шумоподавлен")) return "anc";
        if (name.contains("bluetooth") || name.contains("connect") || name.contains("подключ")) return "connectivity";
        return null;
    }
}
