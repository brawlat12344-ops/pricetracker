package com.example.pricetracker.store;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Достаёт конфигурацию из текста карточки/названия: CPU, GPU, RAM, диск, БП, охлаждение.
 * Используется, когда страница магазина недоступна для парсера ( SPA, 401, 429 ).
 */
@Component
public class ConfigExtractor {

    private static final Pattern CPU = Pattern.compile(
            "(?i)\\b(core i[3579](?:-\\d{4,5})?|i[3579]-\\d{4,5}|ryzen [3579](?: \\d{4}(?:x3d)?)?|apple m[123]|snapdragon \\d gen \\d)");
    private static final Pattern GPU = Pattern.compile(
            "(?i)\\b(rtx ?\\d{4}(?: ti)?|gtx ?\\d{4}|rx ?\\d{4}(?: xt| xtx)?|arc \\w+|iris \\w+)");
    private static final Pattern RAM = Pattern.compile(
            "(?i)(\\d{1,3}) ?(?:gb|гб)\\b[^.]{0,40}(?:ram|оператив|memory|памят)");
    private static final Pattern STORAGE = Pattern.compile(
            "(?i)(\\d{1,4}) ?(?:gb|tb|гб|тб)\\b[^.]{0,40}(?:ssd|nvme|storage|накопител|диск)");
    private static final Pattern PSU = Pattern.compile(
            "(?i)(\\d{3,4}) ?(?:w|вт)\\b[^.]{0,40}(?:psu|блок питания|power supply)");
    private static final Pattern COOLING = Pattern.compile(
            "(?i)(liquid|aio|water|жидкост|air|воздуш|stock|боксов)");

    public Map<String, String> extract(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String value = text.replace('\n', ' ').replace('\r', ' ');
        find(CPU, value, result, "cpu");
        find(GPU, value, result, "gpu");
        find(RAM, value, result, "ram_gb");
        find(STORAGE, value, result, "storage_gb");
        find(PSU, value, result, "psu_w");
        find(COOLING, value, result, "cooling");
        return result;
    }

    private void find(Pattern pattern, String text, Map<String, String> out, String key) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String found = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
            out.put(key, found.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
        }
    }
}
