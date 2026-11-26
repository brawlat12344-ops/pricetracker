# Сервис `nginx`

**Ответственность:** единая точка входа и reverse proxy.

**Образ:** `nginx:1.27-alpine`. Конфигурация копируется из `services/nginx/nginx.conf`.

## API

HTTP на порту `:80` (проброс задаётся `APP_PORT` в `.env`).

| Маршрут | Назначение |
|---|---|
| `/` | прокси на `gateway:8080/` → редирект на `/cheaper` |
| `/cheaper` | прокси на `gateway:8080/cheaper` — единственная страница поиска |
| `/api/` | прокси на `gateway:8080/api/` — REST JSON |
| `/actuator/health` | через `/` → healthcheck gateway |

`catalog:8081` и `price:8082` **не** проксируются: их `/internal/**` доступны только внутри
сети `appnet`. Порты 8081/8082 проброшены на хост лишь для smoke-проверок health и метрик.

Прокси передаёт заголовки `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`.
Таймаут чтения для API — 60 секунд.

**Зависимость:** ждёт `gateway` в состоянии `service_healthy`.
