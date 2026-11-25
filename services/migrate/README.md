# Сервис `migrate`

**Ответственность:** одноразовое применение SQL-миграций Flyway перед стартом `app` и `worker`.

**Профиль запуска:** `migrate`, без веб-сервера (`--spring.main.web-application-type=none`). Завершается кодом `0`.

## API

У сервиса нет HTTP API — это intentional one-shot job. Его контракт — CLI и Flyway:

| Интерфейс | Значение |
|---|---|
| Команда | `java -jar /app/app.jar --spring.profiles.active=migrate --spring.main.web-application-type=none` |
| Источник миграций | `classpath:db/migration` |
| Файл | `src/main/resources/db/migration/V1__init.sql` |
| Результат | `flyway_schema_history`, таблицы `users`, `products`, `price_snapshots`, `alerts` |
| Код выхода | `0` — успех, `1` — ошибка |

## Что создаёт

- уникальный индекс `uq_products_user_url`;
- уникальный индекс `uq_snapshot_product_period` — идемпотентность снимков;
- B-tree `idx_snapshots_product_time`;
- BRIN `idx_snapshots_captured_brin`;
- partial-индексы `idx_alerts_unread`, `idx_alerts_product_unread`.

`application-migrate.yml` отключает JPA-валидацию схемы: на момент запуска таблиц ещё нет.

**Зависимости:** `db` (`condition: service_healthy`).
**Кто ждёт его:** `app` и `worker` (`condition: service_completed_successfully`).
