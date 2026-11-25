# Сервис `nginx`

**Ответственность:** единая точка входа и reverse proxy.

**Образ:** `nginx:1.27-alpine`. Конфигурация копируется из `services/nginx/nginx.conf`.

## API

HTTP на порту `:80` (проброс задаётся `APP_PORT` в `.env`).

| Маршрут | Назначение |
|---|---|
| `/` | прокси на `app:8080/` → редирект на `/cheaper` |
| `/cheaper` | прокси на `app:8080/cheaper` — единственная страница поиска |
| `/api/` | прокси на `app:8080/api/` — REST JSON |
| `/actuator/health` | через `/` → healthcheck приложения |

Прокси передаёт заголовки `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`.
Таймаут чтения для API — 60 секунд.

**Зависимость:** ждёт `app` в состоянии `service_healthy`.
