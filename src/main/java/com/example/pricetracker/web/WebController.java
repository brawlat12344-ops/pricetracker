package com.example.pricetracker.web;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.security.AuthCookie;
import com.example.pricetracker.service.AuthService;
import com.example.pricetracker.store.CheapSearchService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Единственная пользовательская страница — поиск той же конфигурации дешевле. */
@Controller
public class WebController {

    private final AuthService auth;
    private final CategoryCatalog categories;
    private final CheapSearchService cheaper;
    private final int ttlSeconds;

    public WebController(AuthService auth,
                         CategoryCatalog categories,
                         CheapSearchService cheaper,
                         @Value("${jwt.ttl-minutes:120}") int ttlMinutes) {
        this.auth = auth;
        this.categories = categories;
        this.cheaper = cheaper;
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
                          Model model) {
        model.addAttribute("categories", categories.definitions());
        model.addAttribute("cheaperUrl", url == null ? "" : url);
        model.addAttribute("cheaperCategory", category);
        model.addAttribute("cheaperTitle", title == null ? "" : title);
        model.addAttribute("cheaperLimit", limit);
        model.addAttribute("cheaperCrossStore", crossStore == null || crossStore);
        model.addAttribute("result", null);
        model.addAttribute("error", "");

        if (url != null && !url.isBlank()) {
            try {
                CheaperResponse result = cheaper.find(new CheaperRequest(
                        url, category, title, limit, crossStore == null || crossStore));
                model.addAttribute("result", result);
            } catch (Exception ex) {
                model.addAttribute("error", "Не удалось найти дешевле: " + ex.getMessage());
            }
        }
        return "cheaper";
    }
}
