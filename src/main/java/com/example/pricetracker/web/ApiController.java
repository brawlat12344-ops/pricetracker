package com.example.pricetracker.web;

import com.example.pricetracker.builder.BuildService;
import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.dto.LoginRequest;
import com.example.pricetracker.dto.TokenResponse;
import com.example.pricetracker.service.AuthService;
import com.example.pricetracker.store.CheapSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Вход, справочник категорий, поиск дешевле и локальный конфигуратор. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService auth;
    private final CategoryCatalog categories;
    private final CheapSearchService cheaper;
    private final BuildService builder;

    public ApiController(AuthService auth,
                         CategoryCatalog categories,
                         CheapSearchService cheaper,
                         BuildService builder) {
        this.auth = auth;
        this.categories = categories;
        this.cheaper = cheaper;
        this.builder = builder;
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/categories")
    public java.util.List<CategoryCatalog.CategoryDef> categories() {
        return categories.definitions();
    }

    @PostMapping("/cheaper")
    public CheaperResponse cheaper(@Valid @RequestBody CheaperRequest request) {
        return cheaper.find(request);
    }

    @PostMapping("/build")
    public BuildResponse build(@RequestBody BuildRequest request) {
        return builder.build(request);
    }
}
