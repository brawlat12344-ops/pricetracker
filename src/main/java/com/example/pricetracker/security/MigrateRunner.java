package com.example.pricetracker.security;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** One-shot container: applies SQL migrations, then exits with code 0. */
@Profile("migrate")
@Component
public class MigrateRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrateRunner.class);

    private final String url;
    private final String user;
    private final String password;

    public MigrateRunner(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        log.info("migration_completed");
        System.exit(0);
    }
}
