# Сервис `nginx`

**Ответственность:** единая точка входа и reverse proxy.

**Образ:** `nginx:1.27-alpine`. Конфигурация копируется из `services/nginx/nginx.conf`.

## API

HTTP на порту `:80` (проброс задаётся `APP_PORT` в `.env`).

| Маршрут | Назначение |
|---|---|
| `/` | Прокси на `app:8080/` — Thymeleaf UI |
| `/api/` | Прокси на `app:8080/api/` — REST JSON |
| `/actuator/health` | Через `/` → healthcheck приложения |

Прокси передаёт заголовки `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`.
Таймаут чтения для API — 60 секунд.

Опциональный маршрут `/ws/` для WebSocket в конфигурации закомментирован и не реализован.

**Зависимость:** ждёт `app` в состоянии `service_healthy`.
