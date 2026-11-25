# Сервис `migrate`

**Ответственность:** одноразовое применение SQL-миграций Flyway перед стартом `app`.

**Профиль запуска:** `migrate`, без веб-сервера (`--spring.main.web-application-type=none`).
Завершается кодом `0`.

## API

У сервиса нет HTTP API — это one-shot job. Его контракт — CLI и Flyway:

| Интерфейс | Значение |
|---|---|
| Команда | `java -jar /app/app.jar --spring.profiles.active=migrate --spring.main.web-application-type=none` |
| Источник миграций | `classpath:db/migration` |
| Файлы | `V1__init.sql`, `V2__compare.sql`, `V3__demo_users.sql`, `V4__drop_tracking_tables.sql` |
| Результат | `flyway_schema_history` и таблица `users` с 2 демо-аккаунтами |
| Код выхода | `0` — успех, `1` — ошибка |

V1/V2/V3 оставлены как история схемы, чтобы не ломать checksum уже применённых миграций.
`V4` удаляет таблицы `comparisons`, `alerts`, `price_snapshots`, `products` — старые функции
отслеживания удалены из приложения. `V3` создаёт демо-аккаунты
`demo@example.com / demopass123` и `student@example.com / qwerty123` с BCrypt-хешами
(`ON CONFLICT DO NOTHING`).

`application-migrate.yml` отключает JPA-валидацию схемы: на момент запуска таблиц ещё нет.

**Зависимости:** `db` (`condition: service_healthy`).
**Кто ждёт его:** `app` (`condition: service_completed_successfully`).
