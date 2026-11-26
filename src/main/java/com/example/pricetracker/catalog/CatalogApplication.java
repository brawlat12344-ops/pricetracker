package com.example.pricetracker.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Микросервис 2: каталоги категорий/комплектующих, строгая оценка конфигураций
 * и формирование отчётов. Цены получает по HTTP от price-сервиса.
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.example.pricetracker.builder",
                "com.example.pricetracker.cache",
                "com.example.pricetracker.catalog",
                "com.example.pricetracker.compare",
                "com.example.pricetracker.laptop",
                "com.example.pricetracker.provider",
                "com.example.pricetracker.store"
        },
        // Внутренний API не проксируется наружу, поэтому безопасность остаётся только в gateway.
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class, SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
