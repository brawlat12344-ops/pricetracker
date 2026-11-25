# PriceTracker — Java-версия

Stack: **Java 17 + Spring Boot 3.3 + Thymeleaf + Flyway + PostgreSQL 16 + nginx**.
Python, Node.js и Next.js удалены полностью: график рисуется сервером как SVG, UI — серверные шаблоны.

## Запуск

```powershell
cd "C:\Users\user\Desktop\project DevOPs\pricetracker"

$dbPass = [guid]::NewGuid().ToString("N")
$jwtKey = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
@"
POSTGRES_DB=pricetracker
POSTGRES_USER=ptuser
POSTGRES_PASSWORD=$dbPass
POSTGRES_PORT=5432
JWT_SECRET=$jwtKey
ACCESS_TOKEN_TTL_MINUTES=120
POLL_INTERVAL_MS=60000
PRICE_PROVIDER=mock
MOCK_BASE_PRICE=8990
APP_PORT=80
"@ | Set-Content .env -Encoding utf8

docker compose up --build -d
docker compose ps -a
```

Открыть: **http://localhost**

## Сервисы

| Сервис | Папка | Ответственность | Dockerfile | API |
|---|---|---|---|---|
| `db` | `services/db` | PostgreSQL 16, volume `db_data` | `services/db/Dockerfile` | SQL :5432 |
| `migrate` | `services/migrate` | Flyway-миграции, one-shot | `services/migrate/Dockerfile` | CLI / Flyway |
| `app` | `services/app` | REST API + Thymeleaf UI + SVG | `services/app/Dockerfile` | `/api/**`, `/actuator/**` |
| `worker` | `services/worker` | `@Scheduled` опрос цен | `services/worker/Dockerfile` | `/actuator/health`, `PollScheduler` |
| `nginx` | `services/nginx` | reverse proxy, точка входа :80 | `services/nginx/Dockerfile` | `/`, `/api/` |

У каждого сервиса есть отдельная папка с `Dockerfile` и `README.md`, где описан его API:
`services/<имя>/README.md`.

## Документы защиты

- Тема и уникальность: **[docs/topic.md](docs/topic.md)**
- C4 Context / Container / Component: **[docs/c4.md](docs/c4.md)**
- Текстовый протокол проверок: **[docs/checks.md](docs/checks.md)**

## Задача и сценарий

Система следит за ценами товаров: пользователь добавляет URL и целевую цену,
воркер периодически опрашивает цену, история сохраняется в PostgreSQL,
при цене ниже порога создаётся алерт, а график строится сервером как SVG.

Демо-сценарий: зарегистрироваться → добавить товар с порогом 9500 → нажать «Опросить»
→ увидеть цену и алерт → открыть график → убедиться, что повторный опрос в ту же минуту
не создаёт дубль.

## Архитектура

C4-схемы трёх уровней (Context, Container, Component): **[docs/c4.md](docs/c4.md)**.

Взаимодействие сервисов:

- браузер → nginx (HTTP :80);
- nginx → app (`/` → Thymeleaf UI, `/api/` → REST JSON);
- app и worker → PostgreSQL через JDBC/JPA;
- migrate → PostgreSQL через Flyway SQL один раз при старте;
- app и worker → провайдер цены через интерфейс `PriceProvider` (mock или HTML/JSON-LD);
- стартовый порядок задан `depends_on` с `condition`.

## Проверка результата

Полный текстовый протокол с ожидаемыми выводами: **[docs/checks.md](docs/checks.md)**.

### 1. Состояние контейнеров

```powershell
docker compose ps -a
```

Ожидаемый вывод:

```text
pt-app       Up (healthy)
pt-db        Up (healthy)
pt-migrate   Exited (0)
pt-nginx     Up (healthy)
pt-worker    Up (healthy)
```

`Exited (0)` у `pt-migrate` — успешно завершённая одноразовая миграция.

### 2. Healthcheck

```powershell
curl.exe http://localhost/actuator/health
# {"status":"UP"}
```

### 3. Логи воркера

```powershell
docker compose logs -f worker
# worker_cycle_done products=1 created=1 duplicates=0 failed=0
```

### 4. API и идемпотентность

```powershell
docker compose logs -f app | Select-String "snapshot_created|snapshot_duplicate_skipped"
# snapshot_created product_id=... snapshot_id=... price=... provider=mock alert=true
# snapshot_duplicate_skipped product_id=... period_start=...
```

Скриншоты для отчёта: страница «Отслеживаемые товары» с ценой и алертом,
страница товара с SVG-графиком, вывод `docker compose ps -a` и вывод `curl /actuator/health`.

## Демо через curl

```powershell
$token = (curl.exe -s -X POST http://localhost/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"demo@example.com","password":"demopass123"}' | ConvertFrom-Json).access_token

$product = curl.exe -s -X POST http://localhost/api/products `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d '{"url":"https://shop.example/product/42","title":"Кроссовки X","alertThreshold":9500}' | ConvertFrom-Json

$productId = $product.id

curl.exe -s -X POST "http://localhost/api/products/$productId/poll" -H "Authorization: Bearer $token"
curl.exe -s -X POST "http://localhost/api/products/$productId/poll" -H "Authorization: Bearer $token"
curl.exe -s "http://localhost/api/products/$productId/stats?days=7" -H "Authorization: Bearer $token"
curl.exe -s "http://localhost/api/alerts" -H "Authorization: Bearer $token"
```

Второй `/poll` вернёт `"created":false` — идемпотентность через `UNIQUE(product_id, period_start)`.

### Эндпоинты API

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/api/auth/register`, `/api/auth/login` | токен `access_token` |
| `GET/POST` | `/api/products` | список с пагинацией / создать |
| `GET/PATCH/DELETE` | `/api/products/{id}` | получить / изменить / удалить |
| `POST` | `/api/products/{id}/poll` | опросить цену |
| `GET` | `/api/products/{id}/snapshots` | история снимков |
| `GET` | `/api/products/{id}/stats` | агрегация по дням |
| `GET` | `/api/stats/top-drops` | максимальное падение цены |
| `GET` | `/api/alerts` | непрочитанные алерты |
| `POST` | `/api/alerts/{id}/read` | отметить прочитанным |

## Важное

- `docker compose down` сохраняет данные; `down -v` удаляет volume.
- Секреты только в `.env`; `.env` в `.gitignore`.
- JWT для браузера лежит в httpOnly-cookie; для API можно передавать `Authorization: Bearer`.

## Локальная разработка (опционально)

Нужны JDK 17 и Maven:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/pricetracker"
$env:DATABASE_USER="ptuser"
$env:DATABASE_PASSWORD="..."
$env:JWT_SECRET="..."
mvn spring-boot:run
```
