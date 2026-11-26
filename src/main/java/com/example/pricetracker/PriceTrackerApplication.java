package com.example.pricetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** One-shot entrypoint для контейнера migrate; три сервиса имеют свои Application-классы. */
@SpringBootApplication(scanBasePackages = {
        "com.example.pricetracker.security",
        "com.example.pricetracker.domain",
        "com.example.pricetracker.repo"
})
public class PriceTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceTrackerApplication.class, args);
    }
}
