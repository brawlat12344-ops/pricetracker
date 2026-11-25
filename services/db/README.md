# Сервис `db`

**Ответственность:** хранение демо-аккаунтов и истории миграций.

**Образ:** `postgres:16-alpine` (фиксируется в локальном `Dockerfile`).

## API

SQL через PostgreSQL-протокол на порту `5432`.

| Параметр | Значение из `.env` |
|---|---|
| База | `POSTGRES_DB` (по умолчанию `pricetracker`) |
| Пользователь | `POSTGRES_USER` (по умолчанию `ptuser`) |
| Пароль | `POSTGRES_PASSWORD` |
| Внутренний хост | `db` |
| Volume | `db_data` → `/var/lib/postgresql/data` |

```powershell
docker exec -it pt-db psql -U ptuser -d pricetracker
```

### Таблицы

| Таблица | Содержимое |
|---|---|
| `users` | email и bcrypt-хэш пароля демо-аккаунтов |
| `flyway_schema_history` | применённые миграции V1–V4 |

Миграция `V4__drop_tracking_tables.sql` удалила `products`, `price_snapshots`, `alerts`
и `comparisons`: приложение больше не отслеживает товары и не сохраняет отчёты поиска.
Данные сохраняются между перезапусками: `docker compose down` не удаляет volume,
`docker compose down -v` — удаляет.
