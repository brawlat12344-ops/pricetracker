# Сервис `db`

**Ответственность:** хранение всех данных системы.

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

### Подключение из контейнера

```powershell
docker exec -it pt-db psql -U ptuser -d pricetracker
```

### Основные таблицы

| Таблица | Содержимое |
|---|---|
| `users` | email, bcrypt-хэш пароля |
| `products` | URL, название, порог, активность, владелец |
| `price_snapshots` | история цен и интервал снимка |
| `alerts` | сработавшие алерты и флаг прочитанности |
| `flyway_schema_history` | применённые миграции |

Данные сохраняются между перезапусками: `docker compose down` не удаляет volume, `docker compose down -v` — удаляет.
