package com.example.pricetracker.web;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.compare.ComparisonService;
import com.example.pricetracker.dto.AlertResponse;
import com.example.pricetracker.dto.CompareRequest;
import com.example.pricetracker.dto.CompareResponse;
import com.example.pricetracker.dto.PollResponse;
import com.example.pricetracker.dto.ProductResponse;
import com.example.pricetracker.dto.SnapshotResponse;
import com.example.pricetracker.dto.StatsPoint;
import com.example.pricetracker.dto.TokenResponse;
import com.example.pricetracker.security.AuthCookie;
import com.example.pricetracker.service.AuthService;
import com.example.pricetracker.service.ChartSvgService;
import com.example.pricetracker.service.ProductService;
import com.example.pricetracker.service.StatsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
public class WebController {

    private final AuthService auth;
    private final ProductService products;
    private final StatsService stats;
    private final ChartSvgService chart;
    private final int ttlSeconds;
    private final CategoryCatalog categories;
    private final ComparisonService comparisons;

    public WebController(AuthService auth,
                         ProductService products,
                         StatsService stats,
                         ChartSvgService chart,
                         CategoryCatalog categories,
                         ComparisonService comparisons,
                         @Value("${jwt.ttl-minutes:120}") int ttlMinutes) {
        this.auth = auth;
        this.products = products;
        this.stats = stats;
        this.chart = chart;
        this.categories = categories;
        this.comparisons = comparisons;
        this.ttlSeconds = ttlMinutes * 60;
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("error", "");
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletResponse response,
                        Model model) {
        try {
            TokenResponse token = auth.login(email, password);
            AuthCookie.set(response, token.accessToken(), ttlSeconds);
            return "redirect:/";
        } catch (ResponseStatusException ex) {
            model.addAttribute("error", "Неверный email или пароль");
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("error", "");
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           HttpServletResponse response,
                           Model model) {
        try {
            TokenResponse token = auth.register(email, password);
            AuthCookie.set(response, token.accessToken(), ttlSeconds);
            return "redirect:/";
        } catch (ResponseStatusException ex) {
            model.addAttribute("error", "Этот email уже зарегистрирован или пароль слишком короткий");
            return "register";
        }
    }

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String category,
                       @AuthenticationPrincipal UserIdPrincipal principal,
                       Model model,
                       RedirectAttributes redirect) {
        Page<ProductResponse> result = products.list(principal.id(), PageRequest.of(page, 20), category);
        List<AlertResponse> alerts = products.alerts(principal.id(), PageRequest.of(0, 50)).items();
        model.addAttribute("products", result.getContent());
        model.addAttribute("total", result.getTotalElements());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("page", page);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("alerts", alerts);
        model.addAttribute("categories", categories.definitions());
        Object flashError = redirect.getFlashAttributes().get("error");
        model.addAttribute("error", flashError == null ? "" : flashError);
        return "products";
    }

    @GetMapping("/product/{id}")
    public String product(@PathVariable UUID id,
                          @AuthenticationPrincipal UserIdPrincipal principal,
                          Model model,
                          RedirectAttributes redirect) {
        ProductResponse product = products.get(principal.id(), id);
        List<StatsPoint> points = stats.daily(principal.id(), id, 7);
        List<SnapshotResponse> snapshots = stats.snapshots(principal.id(), id, 50);
        List<AlertResponse> alerts = products.alerts(principal.id(), PageRequest.of(0, 100)).items()
                .stream()
                .filter(a -> a.productId().equals(id))
                .toList();
        model.addAttribute("product", product);
        model.addAttribute("chartSvg", chart.render(points));
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("alerts", alerts);
        Object flashMessage = redirect.getFlashAttributes().get("message");
        model.addAttribute("message", flashMessage == null ? "" : flashMessage);
        return "product";
    }

    @PostMapping("/web/products")
    public String addProduct(@RequestParam String url,
                             @RequestParam String title,
                             @RequestParam String alertThreshold,
                             @RequestParam(defaultValue = "laptop") String category,
                             @AuthenticationPrincipal UserIdPrincipal principal,
                             RedirectAttributes redirect) {
        try {
            products.create(principal.id(), new com.example.pricetracker.dto.ProductRequest(
                    url, title, new BigDecimal(alertThreshold), category, null));
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Не удалось добавить товар: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/web/products/{id}/poll")
    public String poll(@PathVariable UUID id,
                       @AuthenticationPrincipal UserIdPrincipal principal,
                       RedirectAttributes redirect) {
        try {
            PollResponse result = products.poll(principal.id(), id);
            redirect.addFlashAttribute("message", result.created()
                    ? "Новый снимок: " + result.price() + " ₽" + (result.alertCreated() ? " — алерт сработал!" : "")
                    : "Снимок уже есть в этом интервале: " + result.price() + " ₽");
        } catch (Exception ex) {
            redirect.addFlashAttribute("message", "Ошибка опроса: " + ex.getMessage());
        }
        return "redirect:/product/" + id;
    }

    @PostMapping("/web/products/{id}/delete")
    public String delete(@PathVariable UUID id,
                         @AuthenticationPrincipal UserIdPrincipal principal) {
        products.delete(principal.id(), id);
        return "redirect:/";
    }

    @GetMapping("/compare")
    public String compare(@RequestParam(name = "ids", required = false) List<UUID> ids,
                          @AuthenticationPrincipal UserIdPrincipal principal,
                          Model model,
                          RedirectAttributes redirect) {
        if (ids == null || ids.size() < 2 || ids.size() > 4) {
            redirect.addFlashAttribute("error", "Для сравнения выберите от 2 до 4 товаров.");
            return "redirect:/";
        }
        try {
            CompareResponse result = comparisons.compare(principal.id(), new CompareRequest(ids, null));
            model.addAttribute("result", result);
            return "compare";
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Не удалось сравнить: " + ex.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/web/alerts/{id}/read")
    public String readAlert(@PathVariable long id,
                            @AuthenticationPrincipal UserIdPrincipal principal) {
        products.markAlertRead(principal.id(), id);
        return "redirect:/";
    }
}
