package com.example.pricetracker.web;

import com.example.pricetracker.dto.*;
import com.example.pricetracker.service.AuthService;
import com.example.pricetracker.service.ProductService;
import com.example.pricetracker.service.StatsService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService auth;
    private final ProductService products;
    private final StatsService stats;

    public ApiController(AuthService auth, ProductService products, StatsService stats) {
        this.auth = auth;
        this.products = products;
        this.stats = stats;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auth.register(request.email(), request.password()));
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/products")
    public Paginated<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        var result = products.list(principal.id(), PageRequest.of(page, size));
        return new Paginated<>(result.getContent(), result.getTotalElements(),
                result.getNumber(), result.getSize());
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(products.create(principal.id(), request));
    }

    @GetMapping("/products/{id}")
    public ProductResponse get(@PathVariable UUID id,
                               @AuthenticationPrincipal UserIdPrincipal principal) {
        return products.get(principal.id(), id);
    }

    @PatchMapping("/products/{id}")
    public ProductResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody ProductPatchRequest request,
                                  @AuthenticationPrincipal UserIdPrincipal principal) {
        return products.update(principal.id(), id, request);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserIdPrincipal principal) {
        products.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products/{id}/poll")
    public PollResponse poll(@PathVariable UUID id,
                             @AuthenticationPrincipal UserIdPrincipal principal) {
        return products.poll(principal.id(), id);
    }

    @GetMapping("/products/{id}/snapshots")
    public java.util.List<SnapshotResponse> snapshots(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return stats.snapshots(principal.id(), id, Math.min(limit, 500));
    }

    @GetMapping("/products/{id}/stats")
    public java.util.List<StatsPoint> dailyStats(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return stats.daily(principal.id(), id, Math.min(Math.max(days, 1), 90));
    }

    @GetMapping("/stats/top-drops")
    public java.util.List<TopDrop> topDrops(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return stats.topDrops(principal.id(), Math.min(Math.max(days, 1), 90),
                Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/alerts")
    public Paginated<AlertResponse> alerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return products.alerts(principal.id(), PageRequest.of(page, size));
    }

    @PostMapping("/alerts/{id}/read")
    public ResponseEntity<Void> readAlert(@PathVariable long id,
                                          @AuthenticationPrincipal UserIdPrincipal principal) {
        products.markAlertRead(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
