package com.example.pricetracker.laptop;

import com.example.pricetracker.builder.PartPriceService;
import com.example.pricetracker.dto.BuildResponse.ComponentOffer;
import com.example.pricetracker.dto.LaptopRequest;
import com.example.pricetracker.dto.LaptopResponse;
import com.example.pricetracker.dto.LaptopResponse.LaptopModel;
import com.example.pricetracker.laptop.LaptopCatalog.Model;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Экспертный подбор ноутбуков: фильтры, сравнение, цены по моделям и отчёт. */
@Service
public class LaptopService {

    private final LaptopCatalog catalog;
    private final PartPriceService prices;

    public LaptopService(LaptopCatalog catalog, PartPriceService prices) {
        this.catalog = catalog;
        this.prices = prices;
    }

    public LaptopResponse select(LaptopRequest request) {
        int budget = request.budgetOrDefault();
        String purpose = request.purposeOrBalanced();
        String priority = request.priorityOrPerformance();
        String screen = request.screenOrAny();
        String os = request.osOrAny();
        String requirements = request.requirementsOrEmpty();
        String region = request.regionOrRussia();

        List<String> warnings = new ArrayList<>();
        BigDecimal ceiling = BigDecimal.valueOf(budget).multiply(BigDecimal.valueOf(1.03));
        BigDecimal wider = BigDecimal.valueOf(budget).multiply(BigDecimal.valueOf(1.25));

        int minRam = parseRequiredRam(requirements.toLowerCase(Locale.ROOT));
        final int requestedRam = minRam;
        if (minRam > 0 && catalog.all().stream().noneMatch(model -> model.ramGb() >= requestedRam)) {
            warnings.add("В справочнике нет моделей с RAM ≥ " + minRam + " ГБ: "
                    + "показаны ближайшие конфигурации с указанием возможностей апгрейда.");
            minRam = 0;
        }

        int effectiveRam = minRam;
        List<Model> matching = filter(screen, os, requirements, effectiveRam, purpose, priority, ceiling);

        if (matching.size() < 3) {
            matching = filter(screen, os, requirements, effectiveRam, purpose, priority, wider);
            warnings.add("В бюджете менее трёх моделей с заданным экраном/ОС: "
                    + "добавлены варианты до +25% к бюджету. Цена указана честно — решите, готовы ли доплатить.");
        }
        if (matching.size() < 3) {
            matching = filter("any", os, requirements, effectiveRam, purpose, priority, wider);
            if (matching.size() >= 3) {
                warnings.add("Моделей с заданным размером экрана мало: показан ближайший размер экрана.");
            }
        }
        if (matching.isEmpty()) {
            warnings.add("Под выбранную ОС и требования в справочнике моделей нет. "
                    + "Попробуйте изменить ОС, регион или бюджет.");
            return emptyResponse(request, budget, purpose, priority, screen, os,
                    requirements, region, warnings);
        }
        if (matching.size() < 3) {
            warnings.add("Найдено меньше трёх моделей: показываем все доступные в справочнике.");
        }
        List<Model> selected = matching.stream().limit(5).toList();

        Map<Model, String> queries = new LinkedHashMap<>();
        for (Model model : selected) {
            queries.put(model, query(model, region, requirements));
        }
        Map<String, List<ComponentOffer>> offers = prices.offersForAll(
                queries.values().stream()
                        .map(query -> new PartPriceService.ComponentQuery(query, basePrice(queries, query)))
                        .toList(),
                warnings);

        List<LaptopModel> models = new ArrayList<>();
        for (Model model : selected) {
            String query = queries.get(model);
            List<ComponentOffer> found = offers.get(query);
            if (found == null || found.isEmpty()) {
                found = prices.demoOffers(query, model.basePrice());
            }
            ComponentOffer first = found.get(0);
            String warranty = first.demo()
                    ? "уточняется — демо-цена, обратитесь к продавцу"
                    : "проверьте гарантию и сервис в своём регионе по ссылке";
            models.add(new LaptopModel(
                    model.sku(), model.name(), model.config(),
                    model.cpu(), model.gpu(), model.ram(), model.ssd(),
                    model.display(), first.price(), first.store(), first.url(),
                    first.availability(), warranty, first.demo(), first.promo(),
                    score(model, purpose, priority),
                    model.cpuMark(), model.gpuMark(), model.batteryHours(),
                    model.brightness(), model.coverage(), model.weightKg(),
                    model.ports(), model.upgrade(), model.pros(), model.cons(),
                    model.pitfalls(), model.forecast()));
        }

        LaptopModel top = models.get(0);
        boolean anyDemo = models.stream().anyMatch(LaptopModel::demo);
        String verdict = "Топ-1: " + top.name() + " — " + money(top.price().longValue())
                + " ₽ (" + top.store() + "), оценка " + top.scoreLabel()
                + ". " + top.pros() + "."
                + (anyDemo ? " Часть цен демо — проверьте по ссылкам." : "");

        List<String> benchmarks = new ArrayList<>();
        for (LaptopModel model : models) {
            benchmarks.add(model.name() + " — CPU mark ≈ " + (int) model.cpuMark()
                    + ", GPU mark ≈ " + (int) model.gpuMark()
                    + ", автономность ≈ " + model.batteryHours() + " ч (справочные тесты, не замер сегодня).");
        }

        List<String> buyingWarnings = new ArrayList<>();
        for (LaptopModel model : models) {
            buyingWarnings.add(model.name() + ": " + model.pitfalls() + " Гарантия: " + warrantyText(model) + ".");
        }
        buyingWarnings.add("Проверьте точный артикул " + top.sku() + " в карточке магазина: "
                + "похожие модели серии отличаются памятью, SSD и матрицей.");

        List<String> priceComparison = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            LaptopModel model = models.get(i);
            List<ComponentOffer> found = offers.get(queries.get(selected.get(i)));
            if (found == null || found.isEmpty()) {
                found = prices.demoOffers(queries.get(selected.get(i)), selected.get(i).basePrice());
            }
            StringBuilder line = new StringBuilder(model.name()).append(": ");
            for (ComponentOffer offer : found) {
                line.append(offer.store()).append(" — ")
                        .append(money(offer.price().longValue())).append(" ₽")
                        .append(offer.demo() ? " (демо)" : "")
                        .append("; ");
            }
            BigDecimal min = found.stream().map(ComponentOffer::price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = found.stream().map(ComponentOffer::price).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            priceComparison.add(line + "разброс " + money(max.subtract(min).longValue())
                    + " ₽; дешевле — " + min + " ₽.");
        }

        String forecast = "Прогноз на 3–5 лет: " + top.name() + " — " + top.forecast()
                + " Узкое место: " + top.cons() + ". "
                + (ramGbOf(top) < 16
                        ? "Меньше 16 ГБ памяти — основной риск при multitasking."
                        : "16 ГБ и SSD NVMe — достаточный запас для задач.");

        if (wishesMentionStore(requirements)) {
            warnings.add("Пожелание «наличие в конкретном магазине» учитывается через ссылки на поиск: "
                    + "фактическое наличие подтвердите в карточке магазина.");
        }
        warnings.add("Цены зафиксированы " + LocalDate.now()
                + "; при акциях и сезонных скидках итог может измениться.");

        return new LaptopResponse(
                budget, purposeLabel(purpose), priorityLabel(priority),
                screenLabel(screen), osLabel(os), requirements, region,
                models, top, verdict, benchmarks, buyingWarnings, priceComparison,
                forecast, anyDemo, LocalDate.now().toString(), dedupe(warnings));
    }

    /** Упрощённые методы доступа к полям модели в отчёте (без изменения DTO). */
    private static int ramGbOf(LaptopModel model) {
        return (int) parseFirstNumber(model.ram());
    }

    private static double parseFirstNumber(String text) {
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return 0;
        }
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String warrantyText(LaptopModel model) {
        return model.warranty();
    }

    private BigDecimal basePrice(Map<Model, String> queries, String query) {
        for (Map.Entry<Model, String> entry : queries.entrySet()) {
            if (entry.getValue().equals(query)) {
                return entry.getKey().basePrice();
            }
        }
        return BigDecimal.valueOf(90000);
    }

    private LaptopResponse emptyResponse(LaptopRequest request, int budget, String purpose,
                                         String priority, String screen, String os,
                                         String requirements, String region, List<String> warnings) {
        return new LaptopResponse(
                budget, purposeLabel(purpose), priorityLabel(priority),
                screenLabel(screen), osLabel(os), requirements, region,
                List.of(), null,
                "Под ваши требования в справочнике моделей не найдено. Измените фильтры.",
                List.of(), List.of(), List.of(),
                "Прогноз недоступен без подобранной модели.",
                false, LocalDate.now().toString(), dedupe(warnings));
    }

    private List<Model> filter(String screen, String os, String requirements, int minRam,
                               String purpose, String priority, BigDecimal ceiling) {
        return catalog.all().stream()
                .filter(model -> model.basePrice().compareTo(ceiling) <= 0)
                .filter(model -> screen.equals("any") || model.screenClass().equals(screen))
                .filter(model -> os.equals("any") || model.os().equals(os))
                .filter(model -> fits(model, requirements, minRam))
                .sorted(Comparator.comparingDouble((Model model) -> score(model, purpose, priority)).reversed())
                .toList();
    }

    private boolean fits(Model model, String requirements, int minRam) {
        if (requirements.isBlank()) {
            return true;
        }
        String text = requirements.toLowerCase(Locale.ROOT);
        if (text.contains("дискрет") || text.contains("rtx") || text.contains("dedicated")) {
            if (!model.discreteGpu()) {
                return false;
            }
        }
        if (text.contains("thunderbolt") || text.contains("tb4")) {
            if (!model.thunderbolt()) {
                return false;
            }
        }
        for (String brand : List.of("apple", "asus", "lenovo", "acer", "hp", "msi")) {
            if (text.contains(brand) && !model.name().toLowerCase(Locale.ROOT).contains(brand)) {
                return false;
            }
        }
        if (minRam > 0 && model.ramGb() < minRam) {
            return false;
        }
        return true;
    }

    private int parseRequiredRam(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() < 2) {
            return 0;
        }
        int value = Integer.parseInt(digits.substring(0, Math.min(2, digits.length())));
        return value >= 4 && value <= 128 ? value : 0;
    }

    private double score(Model model, String purpose, String priority) {
        double cpu = model.cpuMark() / 2400.0 * 100;
        double gpu = model.gpuMark() / 8600.0 * 100;
        double battery = model.batteryHours() / 15.0 * 100;
        double nits = parseFirstNumber(model.brightness());
        double display = nits / 500.0 * 60
                + (model.coverage().contains("100%") ? 40 : 20);
        double weight = (2.5 - model.weightKg()) / 2.5 * 100;

        double result = 0.35 * cpu + 0.30 * gpu + 0.15 * battery
                + 0.10 * display + 0.10 * weight;

        result += switch (purpose) {
            case "game" -> 0.12 * gpu;
            case "montage" -> 0.10 * gpu + 0.05 * cpu;
            case "programming" -> 0.08 * cpu + (model.ramGb() >= 16 ? 4 : 0);
            case "work" -> 0.05 * cpu + 0.03 * display;
            case "study" -> 0.08 * battery + 0.03 * weight;
            default -> 0;
        };

        result += switch (priority) {
            case "battery" -> 0.12 * battery;
            case "weight" -> 0.12 * weight;
            case "screen" -> 0.12 * display;
            case "silence" -> model.quiet() ? 8 : 0;
            default -> 0.05 * cpu + 0.05 * gpu;
        };
        return round1(Math.min(100, Math.max(0, result)) / 10);
    }

    private String query(Model model, String region, String requirements) {
        // Короткий запрос: точная модель + артикул. Длинное описание конфигурации
        // в поиске магазина даёт меньше результатов и маскирует нужный артикул.
        String text = model.name() + " " + model.sku();
        if (!requirements.isBlank()) {
            text += " " + requirements;
        }
        text += " купить " + region;
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 180 ? text.substring(0, 180) : text;
    }

    private boolean wishesMentionStore(String requirements) {
        String text = requirements.toLowerCase(Locale.ROOT);
        return text.contains("магазин") || text.contains("наличи");
    }

    private String purposeLabel(String purpose) {
        return switch (purpose) {
            case "study" -> "Учёба";
            case "work" -> "Работа";
            case "game" -> "Игры";
            case "montage" -> "Монтаж";
            case "programming" -> "Программирование";
            default -> "Универсал";
        };
    }

    private String priorityLabel(String priority) {
        return switch (priority) {
            case "battery" -> "Автономность";
            case "weight" -> "Вес";
            case "screen" -> "Экран";
            case "silence" -> "Тишина";
            default -> "Производительность";
        };
    }

    private String screenLabel(String screen) {
        return switch (screen) {
            case "13_14" -> "13–14″";
            case "15_16" -> "15–16″";
            case "17_plus" -> "17″+";
            default -> "любой";
        };
    }

    private String osLabel(String os) {
        return switch (os) {
            case "windows" -> "Windows";
            case "macos" -> "macOS";
            default -> "без разницы";
        };
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String money(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', '\u00A0');
    }

    private List<String> dedupe(List<String> warnings) {
        return new ArrayList<>(new LinkedHashSet<>(warnings));
    }
}
