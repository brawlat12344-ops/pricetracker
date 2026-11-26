package com.example.pricetracker.builder;

import com.example.pricetracker.cache.CacheKeys;
import com.example.pricetracker.cache.ResponseCache;
import com.example.pricetracker.builder.PartCatalog.Part;
import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.compare.CategoryCatalog.AttributeDef;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.BuildResponse.BuildPart;
import com.example.pricetracker.dto.BuildResponse.BuildVariant;
import com.example.pricetracker.dto.BuildResponse.ComponentOffer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Экспертный конфигуратор: распределяет бюджет, подбирает совместимые детали,
 * проверяет баланс системы и собирает отчёт с предложениями по каждому компоненту.
 */
@Service
public class BuildService {

    private static final BigDecimal MAIN_MARGIN = BigDecimal.valueOf(0.95);
    private static final BigDecimal ALT_FACTOR = BigDecimal.valueOf(1.12);
    private static final BigDecimal MAX_OVER = BigDecimal.valueOf(1.03);

    private final PartCatalog catalog;
    private final CategoryCatalog categories;
    private final PartPriceService prices;
    private final ResponseCache cache;

    public BuildService(PartCatalog catalog, CategoryCatalog categories, PartPriceService prices,
                        ResponseCache cache) {
        this.catalog = catalog;
        this.categories = categories;
        this.prices = prices;
        this.cache = cache;
    }

    public BuildResponse build(BuildRequest request) {
        String key = CacheKeys.key("build", request.categoryOrPc(), request.purposeOrBalanced(),
                request.priorityOrPerformance(), request.wishesOrEmpty(), request.regionOrRussia(),
                request.budgetOrDefault());
        return cache.get("build", key, BuildResponse.class, () -> buildUncached(request));
    }

    private BuildResponse buildUncached(BuildRequest request) {
        String category = request.categoryOrPc();
        String purpose = request.purposeOrBalanced();
        String priority = request.priorityOrPerformance();
        String wishes = request.wishesOrEmpty();
        String region = request.regionOrRussia();
        int budget = request.budgetOrDefault();

        List<String> types = catalog.types(category);
        Map<String, Double> weights = weights(category, purpose, priority, wishes, types);

        Candidate chosen = choose(types, category, weights, budget, priority, wishes, MAIN_MARGIN);
        if (chosen == null) {
            chosen = choose(types, category, weights, budget, priority, wishes, BigDecimal.ONE);
        }
        if (chosen == null || chosen.parts().isEmpty()) {
            throw new IllegalStateException("Не удалось уложиться в бюджет "
                    + money(BigDecimal.valueOf(budget))
                    + " ₽ с проверкой совместимости. Увеличьте бюджет или упростите пожелания.");
        }

        List<String> warnings = new ArrayList<>();
        List<BuildPart> parts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder queries = new StringBuilder();

        List<PartPriceService.ComponentQuery> specs = new ArrayList<>();
        for (Part part : chosen.parts()) {
            specs.add(new PartPriceService.ComponentQuery(
                    componentQuery(part, region, wishes), part.basePrice()));
        }
        Map<String, List<ComponentOffer>> found = prices.offersForAll(specs, warnings);

        for (Part part : chosen.parts()) {
            String query = componentQuery(part, region, wishes);
            queries.append(query).append("; ");
            List<ComponentOffer> offers = found.get(query);
            if (offers == null || offers.isEmpty()) {
                offers = prices.demoOffers(query, part.basePrice());
            }
            BigDecimal price = offers.get(0).price();
            total = total.add(price);
            parts.add(toPart(part, chosen.parts(), price, offers, purpose, priority, wishes));
        }

        BigDecimal allowed = BigDecimal.valueOf(budget).multiply(MAX_OVER);
        if (total.compareTo(allowed) > 0) {
            throw new IllegalStateException("Найденные цены дают " + money(total)
                    + " ₽, это больше бюджета " + money(BigDecimal.valueOf(budget)) + " ₽ более чем на 3%. "
                    + "Увеличьте бюджет или выберите более доступные компоненты.");
        }

        Candidate alt = choose(types, category, weights, budget, priority, wishes, ALT_FACTOR);
        if (alt == null) {
            alt = chosen;
        }
        BigDecimal altBudget = BigDecimal.valueOf(budget).multiply(ALT_FACTOR);
        BuildVariant alternative = alternative(alt, category, region, wishes,
                purpose, priority, total, altBudget);

        double score = round1(scoreOf(chosen, wishes) / 10);
        BigDecimal delta = BigDecimal.valueOf(budget).subtract(total);
        String budgetText = delta.signum() == 0
                ? "точно в бюджете"
                : (delta.signum() < 0 ? "+" : "−") + money(delta.abs()) + " ₽ к бюджету";
        long realOffers = parts.stream().flatMap(part -> part.offers().stream())
                .filter(offer -> !offer.demo()).count();
        long allOffers = parts.stream().mapToLong(part -> part.offers().size()).sum();
        boolean anyDemo = parts.stream().flatMap(part -> part.offers().stream())
                .anyMatch(ComponentOffer::demo);

        String verdict = "Сборка: " + money(total) + " ₽ (" + budgetText + "). "
                + "Оценка " + score + "/10. "
                + "Проверяемых предложений: " + realOffers + " из " + allOffers + "."
                + (anyDemo ? " Есть демо-цены — проверьте по ссылкам." : "");

        if (wishes.toLowerCase(Locale.ROOT).contains("монитор")) {
            addWarning(warnings, "Монитор не входит в перечень компонентов сборки: "
                    + "учтите его отдельной покупкой, если он нужен.");
        }

        ComponentOffer firstOffer = parts.get(0).offers().get(0);
        return new BuildResponse(
                category,
                categories.get(category).label(),
                purposeLabel(purpose),
                priorityLabel(priority),
                wishes,
                region,
                budget,
                parts,
                queries.toString().trim(),
                total,
                firstOffer.store(),
                firstOffer.url(),
                anyDemo,
                score,
                verdict,
                dedupe(warnings),
                rationale(parts),
                weightExplanation(weights, chosen.parts(), purpose, priority, wishes),
                assemblySteps(chosen.parts()),
                bottlenecks(chosen.parts(), anyDemo),
                alternative,
                LocalDate.now().toString());
    }

    private record Candidate(List<Part> parts, BigDecimal total, double score) {
    }

    private Candidate choose(List<String> types, String category, Map<String, Double> weights,
                             int budget, String priority, String wishes, BigDecimal margin) {
        BigDecimal limit = BigDecimal.valueOf(budget).multiply(margin);
        return choose(0, types, category, weights, limit, priority, wishes,
                new ArrayList<>(), BigDecimal.ZERO, 0, 0);
    }

    private Candidate choose(int index, List<String> types, String category,
                             Map<String, Double> weights, BigDecimal limit,
                             String priority, String wishes, List<Part> chosen,
                             BigDecimal total, double weighted, double weightSum) {
        if (index == types.size()) {
            double score = weightSum == 0 ? 0 : weighted / weightSum;
            return new Candidate(List.copyOf(chosen), total, score);
        }

        String type = types.get(index);
        double weight = weights.getOrDefault(type, 1.0);
        Candidate best = null;
        for (Part part : catalog.parts(category)) {
            if (!part.type().equals(type) || !compatible(type, part, chosen, priority)) {
                continue;
            }
            BigDecimal nextTotal = total.add(part.basePrice());
            if (nextTotal.compareTo(limit) > 0) {
                continue;
            }
            chosen.add(part);
            Candidate candidate = choose(index + 1, types, category, weights, limit, priority, wishes,
                    chosen, nextTotal, weighted + weight * part.score(), weightSum + weight);
            chosen.remove(chosen.size() - 1);
            if (candidate != null && (best == null || better(candidate, best, wishes))) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean better(Candidate candidate, Candidate current, String wishes) {
        double left = scoreOf(candidate, wishes);
        double right = scoreOf(current, wishes);
        if (Math.abs(left - right) > 0.0001) {
            return left > right;
        }
        return candidate.total().compareTo(current.total()) < 0;
    }

    private double scoreOf(Candidate candidate, String wishes) {
        if (!wishes.toLowerCase(Locale.ROOT).contains("rgb")) {
            return candidate.score();
        }
        long rgb = candidate.parts().stream()
                .filter(part -> "true".equals(part.traits().get("rgb")))
                .count();
        return Math.min(100, candidate.score() + rgb * 3);
    }

    private boolean compatible(String type, Part part, List<Part> chosen, String priority) {
        Part cpu = find(chosen, "cpu");
        Part gpu = find(chosen, "gpu");
        Part board = find(chosen, "motherboard");
        Part cooling = find(chosen, "cooling");
        Part box = find(chosen, "case");

        return switch (type) {
            case "motherboard" -> {
                if (cpu != null && !part.traits().get("socket").equals(cpu.traits().get("socket"))) {
                    yield false;
                }
                String form = part.traits().get("form");
                yield !("compact".equals(priority) && !"mATX".equals(form))
                        && !("upgrade".equals(priority) && "mATX".equals(form));
            }
            case "ram_gb" -> {
                String memory = board != null ? board.traits().get("memory")
                        : cpu == null ? null : cpu.traits().get("memory");
                yield memory == null || memory.equals(part.traits().get("memory"));
            }
            case "psu_w" -> {
                double required = cpuTdp(cpu) + gpuTdp(gpu) + 150;
                double watts = number(part, "watts");
                yield watts >= required
                        && !("upgrade".equals(priority) && watts < 750);
            }
            case "cooling" -> {
                boolean socketOk = cpu == null
                        || part.traits().getOrDefault("sockets", "").contains(cpu.traits().get("socket"));
                boolean quietEnough = !("silence".equals(priority) && part.score() < 60);
                yield socketOk && quietEnough;
            }
            case "case" -> {
                boolean boardOk = board == null
                        || part.traits().getOrDefault("supports", "").contains(board.traits().get("form"));
                boolean gpuOk = gpu == null
                        || number(part, "gpuLength") >= number(gpu, "length");
                boolean coolerOk = cooling == null || coolerFits(part, cooling);
                String form = part.traits().get("form");
                boolean compactOk = !"compact".equals(priority) || "mATX".equals(form);
                boolean upgradeOk = !"upgrade".equals(priority) || !"mATX".equals(form);
                yield boardOk && gpuOk && coolerOk && compactOk && upgradeOk;
            }
            default -> true;
        };
    }

    private boolean coolerFits(Part box, Part cooling) {
        String radiator = cooling.traits().get("radiator");
        if (radiator != null) {
            return number(box, "radiator") >= numberFrom(radiator);
        }
        return number(box, "coolerHeight") >= number(cooling, "height");
    }

    private Map<String, Double> weights(String category, String purpose, String priority,
                                        String wishes, List<String> types) {
        Map<String, Double> result = new LinkedHashMap<>();
        Map<String, Double> base = new LinkedHashMap<>();
        for (AttributeDef attribute : categories.get(category).attributes()) {
            base.put(attribute.key(), attribute.weight());
        }
        for (String type : types) {
            result.put(type, base.getOrDefault(type, defaultWeight(type)));
        }

        switch (purpose) {
            case "game" -> {
                boost(result, types, "gpu", 25);
                boost(result, types, "cpu", 5);
            }
            case "work" -> {
                boost(result, types, "cpu", 20);
                boost(result, types, "ram_gb", 15);
                boost(result, types, "storage_gb", 5);
            }
            case "montage" -> {
                boost(result, types, "cpu", 15);
                boost(result, types, "gpu", 15);
                boost(result, types, "ram_gb", 15);
                boost(result, types, "storage_gb", 10);
            }
            case "3d" -> {
                boost(result, types, "cpu", 15);
                boost(result, types, "gpu", 20);
                boost(result, types, "ram_gb", 10);
            }
            case "streaming" -> {
                boost(result, types, "cpu", 20);
                boost(result, types, "gpu", 10);
                boost(result, types, "ram_gb", 10);
            }
            case "study" -> {
                boost(result, types, "storage_gb", 10);
                boost(result, types, "cpu", 5);
            }
            default -> {
                // Универсальный профиль использует базовые веса категории.
            }
        }

        switch (priority) {
            case "silence" -> {
                boost(result, types, "cooling", 30);
                boost(result, types, "psu_w", 5);
            }
            case "compact" -> {
                boost(result, types, "case", 25);
                boost(result, types, "motherboard", 15);
            }
            case "upgrade" -> {
                boost(result, types, "psu_w", 15);
                boost(result, types, "motherboard", 15);
                boost(result, types, "case", 10);
                boost(result, types, "ram_gb", 5);
            }
            default -> boost(result, types, "cpu", 5);
        }
        return result;
    }

    private double defaultWeight(String type) {
        return switch (type) {
            case "motherboard" -> 6;
            case "screen", "battery_mah", "weight_kg" -> 10;
            default -> 5;
        };
    }

    private void boost(Map<String, Double> weights, List<String> types, String key, double extra) {
        if (types.contains(key)) {
            weights.merge(key, extra, Double::sum);
        }
    }

    private BuildPart toPart(Part part, List<Part> all, BigDecimal price,
                             List<ComponentOffer> offers, String purpose,
                             String priority, String wishes) {
        return new BuildPart(
                part.type(), part.label(), part.name(), part.spec(),
                Math.round(part.score()) / 10.0, price,
                reason(part, all, purpose, priority, wishes),
                compatibilityText(part, all),
                offers);
    }

    private String componentQuery(Part part, String region, String wishes) {
        StringBuilder text = new StringBuilder(part.name()).append(' ').append(part.spec());
        if (!wishes.isBlank()) {
            String extra = wishes.length() > 60 ? wishes.substring(0, 60) : wishes;
            text.append(' ').append(extra);
        }
        text.append(" купить ").append(region);
        String query = text.toString().replaceAll("\\s+", " ").trim();
        return query.length() > 220 ? query.substring(0, 220) : query;
    }

    private BuildVariant alternative(Candidate candidate, String category, String region,
                                     String wishes, String purpose, String priority,
                                     BigDecimal mainTotal, BigDecimal altBudget) {
        BigDecimal total = BigDecimal.ZERO;
        List<BuildPart> parts = new ArrayList<>();
        for (Part part : candidate.parts()) {
            String query = componentQuery(part, region, wishes);
            List<ComponentOffer> offers = prices.demoOffers(query, part.basePrice());
            BigDecimal price = offers.get(0).price();
            total = total.add(price);
            parts.add(toPart(part, candidate.parts(), price, offers, purpose, priority, wishes));
        }
        String summary = "Ориентировочно " + money(total.subtract(mainTotal)) + " ₽ к основной сборке; "
                + "цены демо-каталога, проверьте по ссылкам.";
        return new BuildVariant("Альтернативная сборка (+12%)", altBudget, total,
                round1(scoreOf(candidate, wishes) / 10), parts, summary, true);
    }

    private List<String> rationale(List<BuildPart> parts) {
        List<String> result = new ArrayList<>();
        for (BuildPart part : parts) {
            result.add(part.label() + " — " + part.name() + ": " + part.reason()
                    + " Совместимость: " + part.compatibility() + ".");
        }
        return result;
    }

    /** Прозрачность весов: какие требования усилили CPU/GPU и как считается оценка. */
    private List<String> weightExplanation(Map<String, Double> weights, List<Part> chosen,
                                           String purpose, String priority, String wishes) {
        List<String> result = new ArrayList<>();
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        result.add("Профиль «" + purposeLabel(purpose) + "» и приоритет «" + priorityLabel(priority)
                + "»: сумма весов " + Math.round(sum)
                + ". Вес определяет долю баллов компонента в итоговой оценке 0–10.");
        weights.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(4)
                .forEach(entry -> result.add(labelFor(entry.getKey()) + " — вес "
                        + Math.round(entry.getValue()) + " ("
                        + (sum == 0 ? 0 : Math.round(entry.getValue() * 100 / sum))
                        + "% итоговой оценки)."));

        Part cpu = find(chosen, "cpu");
        Part gpu = find(chosen, "gpu");
        if (cpu != null) {
            result.add("CPU " + cpu.name() + ": вес " + Math.round(weight(weights, "cpu"))
                    + ", " + reason(cpu, chosen, purpose, priority, ""));
        }
        if (gpu != null) {
            result.add("GPU " + gpu.name() + ": вес " + Math.round(weight(weights, "gpu"))
                    + ", " + reason(gpu, chosen, purpose, priority, ""));
        }
        if (wishes.toLowerCase(Locale.ROOT).contains("rgb")) {
            result.add("Пожелание RGB добавляет до +3 балла за каждый компонент с подсветкой.");
        }
        result.add("Совместимость, бюджет и наличие проверяются отдельно и в оценку не входят.");
        return result;
    }

    private String labelFor(String type) {
        return switch (type) {
            case "cpu" -> "Процессор";
            case "gpu" -> "Видеокарта";
            case "motherboard" -> "Материнская плата";
            case "ram_gb" -> "Оперативная память";
            case "storage_gb" -> "SSD";
            case "psu_w" -> "Блок питания";
            case "cooling" -> "Охлаждение";
            case "case" -> "Корпус";
            case "screen" -> "Экран";
            case "battery_mah" -> "Автономность";
            case "weight_kg" -> "Вес";
            default -> type;
        };
    }

    private double weight(Map<String, Double> weights, String type) {
        return weights.getOrDefault(type, 0.0);
    }

    private List<String> assemblySteps(List<Part> parts) {
        Part cpu = find(parts, "cpu");
        Part board = find(parts, "motherboard");
        Part ram = find(parts, "ram_gb");
        Part ssd = find(parts, "storage_gb");
        Part gpu = find(parts, "gpu");
        Part psu = find(parts, "psu_w");
        Part cooling = find(parts, "cooling");
        Part box = find(parts, "case");
        String name = partName(parts, "case", "корпус");
        List<String> steps = new ArrayList<>();
        steps.add("1. Подготовьте антистатический браслет и откройте " + name + ".");
        steps.add("2. Установите " + nameOf(cpu) + " в сокет " + trait(board, "socket")
                + " матплаты " + nameOf(board) + ", затем закрепите " + nameOf(cooling) + ".");
        steps.add("3. Вставьте " + nameOf(ram) + " в соответствующие слоты и закрепите "
                + nameOf(ssd) + " в разъёме M.2.");
        steps.add("4. Закрепите матплату в корпусе, подключите переднюю панель и USB.");
        steps.add("5. Установите " + nameOf(gpu) + " (длина " + trait(gpu, "length")
                + " мм), подключите кабель PCIe.");
        steps.add("6. Установите " + nameOf(psu) + ", подключите 24-pin, 8-pin CPU и PCIe, "
                + "проверьте укладку кабелей.");
        steps.add("7. Первый запуск: зайдите в BIOS, включите профиль памяти, "
                + "проверьте температуры и запустите стресс-тест.");
        return steps;
    }

    private List<String> bottlenecks(List<Part> parts, boolean anyDemo) {
        Part cpu = find(parts, "cpu");
        Part gpu = find(parts, "gpu");
        Part psu = find(parts, "psu_w");
        Part box = find(parts, "case");
        List<String> result = new ArrayList<>();

        if (cpu != null && gpu != null && Math.abs(cpu.score() - gpu.score()) > 18) {
            String weak = cpu.score() < gpu.score() ? "процессор" : "видеокарта";
            result.add("Баланс: " + weak + " заметно отстаёт. В первую очередь замените именно его.");
        } else {
            result.add("Баланс: процессор и видеокарта подобраны без явного «бутылочного горлышка».");
        }

        double required = cpuTdp(cpu) + gpuTdp(gpu) + 150;
        double watts = number(psu, "watts");
        if (watts < required * 1.2) {
            result.add("Запас БП невелик: при апгрейде GPU замените БП на 1000 Вт.");
        } else {
            result.add("Запас БП: " + (int) watts + " Вт против расчётных " + (int) required
                    + " Вт — есть резерв под апгрейд.");
        }

        if (box != null && gpu != null
                && number(box, "gpuLength") - number(gpu, "length") < 30) {
            result.add("Корпус почти вплотную к видеокарте: при смене GPU проверьте длину.");
        }

        result.add("Варианты апгрейда: GPU → RTX 4080 16GB, RAM → 64 ГБ DDR5, "
                + "SSD → 4 ТБ NVMe, охлаждение → AIO 240.");
        if (anyDemo) {
            result.add("Часть цен — демо-расчёт: перед покупкой проверьте наличие и цену по ссылкам.");
        }
        return result;
    }

    private String reason(Part part, List<Part> all, String purpose, String priority, String wishes) {
        String task = purposeLabel(purpose).toLowerCase(Locale.ROOT);
        return switch (part.type()) {
            case "cpu" -> "процессор под задачу: " + task + "; сокет " + trait(part, "socket");
            case "gpu" -> "графика подобрана под задачу: " + task
                    + "; длина " + trait(part, "length") + " мм";
            case "motherboard" -> "чипсет " + trait(part, "chipset") + ", сокет "
                    + trait(part, "socket") + ", память " + trait(part, "memory");
            case "ram_gb" -> "запас памяти для " + task + ": " + part.name();
            case "storage_gb" -> "объём SSD для проектов, игр и системы";
            case "psu_w" -> "запас мощности " + trait(part, "watts")
                    + " Вт при расчётной нагрузке " + (int) requiredPower(all) + " Вт";
            case "cooling" -> "совместим с сокетом " + trait(find(all, "cpu"), "socket")
                    + "; " + part.spec();
            case "case" -> "поддерживает матплату " + trait(find(all, "motherboard"), "form")
                    + " и видеокарту до " + trait(part, "gpuLength") + " мм";
            case "screen" -> "экран для " + task;
            case "battery_mah" -> "автономность для " + task;
            case "weight_kg" -> "вес устройства для " + task;
            default -> "выбрано локальным конфигуратором";
        };
    }

    private String compatibilityText(Part part, List<Part> all) {
        return switch (part.type()) {
            case "motherboard" -> "сокет " + trait(part, "socket") + ", память "
                    + trait(part, "memory") + ", форм-фактор " + trait(part, "form");
            case "ram_gb" -> "память " + trait(part, "memory") + " совпадает с матплатой";
            case "psu_w" -> trait(part, "watts") + " Вт ≥ " + (int) requiredPower(all) + " Вт";
            case "cooling" -> "сокеты: " + part.traits().getOrDefault("sockets", "—");
            case "case" -> "поддержка " + part.traits().getOrDefault("supports", "—")
                    + ", GPU до " + trait(part, "gpuLength") + " мм";
            case "gpu" -> "длина " + trait(part, "length") + " мм ≤ "
                    + trait(find(all, "case"), "gpuLength") + " мм корпуса";
            default -> "проверено конфигуратором";
        };
    }

    private double requiredPower(List<Part> parts) {
        return cpuTdp(find(parts, "cpu")) + gpuTdp(find(parts, "gpu")) + 150;
    }

    private double cpuTdp(Part cpu) {
        return number(cpu, "tdp");
    }

    private double gpuTdp(Part gpu) {
        return number(gpu, "tdp");
    }

    private Part find(List<Part> parts, String type) {
        return parts.stream().filter(part -> part.type().equals(type)).findFirst().orElse(null);
    }

    private String nameOf(Part part) {
        return part == null ? "компонент" : part.name();
    }

    private String partName(List<Part> parts, String type, String fallback) {
        Part part = find(parts, type);
        return part == null ? fallback : part.name();
    }

    private String trait(Part part, String key) {
        return part == null ? "—" : part.traits().getOrDefault(key, "—");
    }

    private double number(Part part, String key) {
        return part == null ? 0 : numberFrom(part.traits().getOrDefault(key, "0"));
    }

    private double numberFrom(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String purposeLabel(String purpose) {
        return switch (purpose) {
            case "game" -> "Игры";
            case "work" -> "Работа";
            case "montage" -> "Монтаж";
            case "3d" -> "3D";
            case "streaming" -> "Стриминг";
            case "study" -> "Учёба";
            default -> "Универсально";
        };
    }

    private String priorityLabel(String priority) {
        return switch (priority) {
            case "silence" -> "Тишина";
            case "compact" -> "Компактность";
            case "upgrade" -> "Апгрейд";
            default -> "Производительность";
        };
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String money(BigDecimal value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', '\u00A0');
    }

    private void addWarning(List<String> warnings, String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private List<String> dedupe(List<String> warnings) {
        return new ArrayList<>(new LinkedHashSet<>(warnings));
    }
}
