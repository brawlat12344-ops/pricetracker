package com.example.pricetracker.analog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Источник карточек-аналогов. Демо-каталог и реальный поиск реализуют этот контракт. */
public interface AnalogSource {

    String name();

    List<Candidate> find(String sourceUrl, String category, String title, int limit);

    /**
     * price и specs могут быть пустыми — тогда их дозагружают реальные провайдеры.
     * Демо-каталог Заполняет оба поля сразу и не требует сети.
     */
    record Candidate(String url, String title, BigDecimal price, Map<String, String> specs) {

        public static Candidate link(String url, String title) {
            return new Candidate(url, title, null, Map.of());
        }
    }

    class SourceException extends RuntimeException {
        public SourceException(String message) {
            super(message);
        }

        public SourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
