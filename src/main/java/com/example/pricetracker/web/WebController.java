package com.example.pricetracker.web;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.dto.LaptopRequest;
import com.example.pricetracker.dto.LaptopResponse;
import com.example.pricetracker.gateway.CatalogClient;
import com.example.pricetracker.security.AuthCookie;
import com.example.pricetracker.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Единственная пользовательская страница с двумя режимами: поиск дешевле и конфигуратор. */
@Controller
public class WebController {

    private final AuthService auth;
    private final CatalogClient catalog;
    private final int ttlSeconds;

    public WebController(AuthService auth,
                         CatalogClient catalog,
                         @Value("${jwt.ttl-minutes:120}") int ttlMinutes) {
        this.auth = auth;
        this.catalog = catalog;
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
            var token = auth.login(email, password);
            AuthCookie.set(response, token.accessToken(), ttlSeconds);
            return "redirect:/cheaper";
        } catch (ResponseStatusException ex) {
            model.addAttribute("error", "Неверный email или пароль");
            return "login";
        }
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/cheaper";
    }

    @GetMapping("/cheaper")
    public String cheaper(@RequestParam(required = false) String url,
                          @RequestParam(defaultValue = "pc") String category,
                          @RequestParam(required = false) String title,
                          @RequestParam(defaultValue = "10") int limit,
                          @RequestParam(required = false) Boolean crossStore,
                          @RequestParam(required = false) BigDecimal sourcePrice,
                          @RequestParam(defaultValue = "url") String mode,
                          @RequestParam(defaultValue = "balanced") String purpose,
                          @RequestParam(defaultValue = "performance") String priority,
                          @RequestParam(required = false) String wishes,
                          @RequestParam(defaultValue = "Россия") String region,
                          @RequestParam(defaultValue = "any") String screen,
                          @RequestParam(defaultValue = "any") String os,
                          @RequestParam(required = false) String requirements,
                          @RequestParam(required = false) Integer budget,
                          Model model) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("cheaperUrl", url == null ? "" : url);
        model.addAttribute("cheaperCategory", category);
        model.addAttribute("cheaperTitle", title == null ? "" : title);
        model.addAttribute("cheaperLimit", limit);
        model.addAttribute("cheaperCrossStore", crossStore == null || crossStore);
        model.addAttribute("cheaperSourcePrice", sourcePrice == null ? "" : sourcePrice.toPlainString());
        model.addAttribute("mode", "build".equals(mode) ? "build" : "url");
        model.addAttribute("builderCategory", "laptop".equalsIgnoreCase(category) ? "laptop" : "pc");
        model.addAttribute("builderPurpose", purpose == null ? "balanced" : purpose);
        model.addAttribute("builderPriority", priority == null ? "performance" : priority);
        model.addAttribute("builderWishes", wishes == null ? "" : wishes);
        model.addAttribute("builderRegion", region == null || region.isBlank() ? "Россия" : region);
        model.addAttribute("builderScreen", screen == null || screen.isBlank() ? "any" : screen);
        model.addAttribute("builderOs", os == null || os.isBlank() ? "any" : os);
        model.addAttribute("builderRequirements", requirements == null ? "" : requirements);
        model.addAttribute("builderBudget", budget == null ? 120000 : budget);
        model.addAttribute("result", null);
        model.addAttribute("buildResult", null);
        model.addAttribute("laptopResult", null);
        model.addAttribute("error", "");

        try {
            if ("build".equals(mode) && "laptop".equalsIgnoreCase(category)) {
                LaptopResponse laptopsResult = catalog.laptops(new LaptopRequest(
                        budget, purpose, priority, screen, os, requirements, region));
                model.addAttribute("laptopResult", laptopsResult);
            } else if ("build".equals(mode)) {
                BuildResponse build = catalog.build(new BuildRequest(
                        category, purpose, priority, wishes, region, budget));
                model.addAttribute("buildResult", build);
            } else if (url != null && !url.isBlank()) {
                CheaperResponse result = catalog.cheaper(new CheaperRequest(
                        url, category, title, limit, crossStore == null || crossStore, sourcePrice));
                model.addAttribute("result", result);
            }
        } catch (Exception ex) {
            model.addAttribute("error", "Не удалось выполнить запрос: " + ex.getMessage());
        }
        return "cheaper";
    }
}
