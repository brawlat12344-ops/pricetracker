package com.example.pricetracker.price;

import com.example.pricetracker.store.CheapSearchService;
import com.example.pricetracker.store.ConfigExtractor;
import com.example.pricetracker.store.DemoCheaperSource;
import com.example.pricetracker.store.SameConfigMatcher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan;

/**
 * Микросервис 3: единственный, кто ходит на сайты магазинов.
 * Классы доменной оценки исключены — они живут в catalog.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class,
        SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class})
@ComponentScan(basePackages = {"com.example.pricetracker.price", "com.example.pricetracker.store"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                CheapSearchService.class, DemoCheaperSource.class, SameConfigMatcher.class,
                ConfigExtractor.class
        }))
public class PriceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceApplication.class, args);
    }
}
