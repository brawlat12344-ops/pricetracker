package com.example.pricetracker.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Микросервис 1: пользовательский интерфейс, JWT/куки и HTTP-фасад.
 * Доменная логика и парсинг вынесены в catalog и price соответственно.
 */
@EnableJpaRepositories("com.example.pricetracker.repo")
@EntityScan("com.example.pricetracker.domain")
@SpringBootApplication(scanBasePackages = {
        "com.example.pricetracker.web",
        "com.example.pricetracker.security",
        "com.example.pricetracker.service",
        "com.example.pricetracker.repo",
        "com.example.pricetracker.domain",
        "com.example.pricetracker.gateway"
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
