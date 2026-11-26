package com.example.pricetracker.web;

import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.dto.LaptopRequest;
import com.example.pricetracker.dto.LaptopResponse;
import com.example.pricetracker.dto.LoginRequest;
import com.example.pricetracker.dto.TokenResponse;
import com.example.pricetracker.gateway.CatalogClient;
import com.example.pricetracker.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Вход, справочник категорий и HTTP-фасад над микросервисом catalog. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService auth;
    private final CatalogClient catalog;

    public ApiController(AuthService auth, CatalogClient catalog) {
        this.auth = auth;
        this.catalog = catalog;
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/categories")
    public java.util.List<CategoryCatalog.CategoryDef> categories() {
        return catalog.categories();
    }

    @PostMapping("/cheaper")
    public CheaperResponse cheaper(@Valid @RequestBody CheaperRequest request) {
        return catalog.cheaper(request);
    }

    @PostMapping("/build")
    public BuildResponse build(@RequestBody BuildRequest request) {
        return catalog.build(request);
    }

    @PostMapping("/laptops")
    public LaptopResponse laptops(@RequestBody LaptopRequest request) {
        return catalog.laptops(request);
    }

    /** Живая проверка цепочки трёх микросервисов. */
    @GetMapping("/topology")
    public Map<String, Object> topology() {
        return Map.of("service", "gateway", "catalog", catalog.info());
    }
}
