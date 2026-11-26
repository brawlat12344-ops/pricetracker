package com.example.pricetracker.store;

import com.example.pricetracker.dto.CheaperRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheapLinksTest {

    private final SearchQueryBuilder queries = new SearchQueryBuilder();
    private final StoreRegistry stores = new StoreRegistry();

    @Test
    void searchQueryUsesOnlyCpuGpuAndMemory() {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("cpu", "core i5-12400");
        specs.put("gpu", "rtx 4070");
        specs.put("ram_gb", "32");
        specs.put("storage_gb", "1024");
        specs.put("psu_w", "650");
        specs.put("os", "windows 11");
        specs.put("case", "compact");

        String query = queries.build("pc", specs, " Core i5 RTX 4070 32 ГБ ");

        assertTrue(query.startsWith("сборка ПК core i5-12400 rtx 4070 32 ГБ"), query);
        assertFalse(query.contains("БП"), query);
        assertFalse(query.contains("Windows"), query);
        assertFalse(query.contains("compact"), query);
    }

    @Test
    void laptopQueryUsesLaptopPrefixAndTitleFallback() {
        String query = queries.build("laptop", Map.of("cpu", "Core i7-1365U", "ram_gb", "16"),
                "Ноутбук с/Core i7-1365U/16 ГБ");
        assertTrue(query.startsWith("ноутбук core i7-1365u 16 ГБ"), query);
        assertEquals("ноутбук", queries.build("laptop", Map.of(), "  "));
    }

    @Test
    void mvideoLinkPointsToWorkingSearchPage() {
        String url = stores.searchUrl(stores.byKey("mvideo"), "core i5 rtx 4070 32 ГБ", "mvideo.ru");
        assertTrue(url.startsWith("https://www.mvideo.ru/search?q="), url);
        assertFalse(url.contains("/products/search"), url);
    }

    @Test
    void sourcePriceIsOptionalAndPreserved() {
        assertNull(new CheaperRequest("https://example.com/pc/1", "pc", "title", 5, true)
                .knownSourcePrice());
        assertEquals(new BigDecimal("89990"),
                new CheaperRequest("https://example.com/pc/1", "pc", "title", 5, true,
                        new BigDecimal("89990")).knownSourcePrice());
    }
}
