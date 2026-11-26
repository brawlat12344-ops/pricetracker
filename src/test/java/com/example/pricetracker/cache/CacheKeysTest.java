package com.example.pricetracker.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheKeysTest {

    @Test
    void normalizesUrlHostCaseTrailingSlashFragmentAndQueryOrder() {
        assertEquals("https://dns-shop.ru/product/1?a=1&b=2",
                CacheKeys.url("HTTPS://DNS-Shop.ru/product/1/?b=2&a=1#top"));
    }

    @Test
    void normalizesText() {
        assertEquals("core i5 rtx 4070", CacheKeys.text("  Core   I5  RTX 4070  "));
    }

    @Test
    void buildsStableKey() {
        String first = CacheKeys.key("build", "pc", "game", 120000);
        String second = CacheKeys.key("build", "PC", "GAME", 120000);
        assertEquals(first, second);
    }
}
