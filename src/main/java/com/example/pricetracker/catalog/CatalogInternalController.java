package com.example.pricetracker.catalog;

import com.example.pricetracker.builder.BuildService;
import com.example.pricetracker.compare.CategoryCatalog;
import com.example.pricetracker.dto.BuildRequest;
import com.example.pricetracker.dto.BuildResponse;
import com.example.pricetracker.dto.CheaperRequest;
import com.example.pricetracker.dto.CheaperResponse;
import com.example.pricetracker.dto.LaptopRequest;
import com.example.pricetracker.dto.LaptopResponse;
import com.example.pricetracker.laptop.LaptopService;
import com.example.pricetracker.store.CheapSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Внутренний API: доступен только внутри сети appnet (не проксируется nginx). */
@RestController
@RequestMapping("/internal")
public class CatalogInternalController {

    private final CategoryCatalog categories;
    private final CheapSearchService cheaper;
    private final BuildService builder;
    private final LaptopService laptops;
    private final PriceClient price;

    public CatalogInternalController(CategoryCatalog categories, CheapSearchService cheaper,
                                     BuildService builder, LaptopService laptops, PriceClient price) {
        this.categories = categories;
        this.cheaper = cheaper;
        this.builder = builder;
        this.laptops = laptops;
        this.price = price;
    }

    @GetMapping("/categories")
    public List<CategoryCatalog.CategoryDef> categories() {
        return categories.definitions();
    }

    @PostMapping("/cheaper")
    public CheaperResponse cheaper(@RequestBody CheaperRequest request) {
        return cheaper.find(request);
    }

    @PostMapping("/build")
    public BuildResponse build(@RequestBody BuildRequest request) {
        return builder.build(request);
    }

    @PostMapping("/laptops")
    public LaptopResponse laptops(@RequestBody LaptopRequest request) {
        return laptops.select(request);
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("service", "catalog", "price", price.info());
    }
}
