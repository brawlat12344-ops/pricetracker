# PriceTracker — Java-версия

![PriceTracker — тёмный баннер](docs/assets/pricetracker-banner.png)

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
SPEC_PROVIDER=mock
MOCK_BASE_PRICE=8990
APP_PORT=80
"@ | Set-Content .env -Encoding utf8

docker compose up --build -d
docker compose ps -a
```

Открыть: **http://localhost**

**Регистрация закрыта.** Вход по готовым аккаунтам из миграции `V3__demo_users.sql`:
- `demo@example.com` / `demopass123`
- `student@example.com` / `qwerty123`

## Быстрый запуск одним кликом (Windows)

Двойной клик по `PriceTracker.exe` в корне проекта:

- проверит и при необходимости запустит Docker Desktop;
- выполнит `docker compose up -d`;
- дождётся `{"status":"UP"}`;
- откроет **http://localhost** в браузере.

Без открытия браузера:

```powershell
.\PriceTracker.exe --no-open
```

Исходник и инструкция по сборке: `tools/launcher/README.md`.

## Сервисы

| Сервис | Папка | Ответственность | Dockerfile | API |
|---|---|---|---|---|
| `db` | `services/db` | PostgreSQL 16, volume `db_data` | `services/db/Dockerfile` | SQL :5432 |
| `migrate` | `services/migrate` | Flyway-миграции V1 + V2, one-shot | `services/migrate/Dockerfile` | CLI / Flyway |
| `app` | `services/app` | REST API + Thymeleaf UI + SVG + сравнение товаров | `services/app/Dockerfile` | `/api/**`, `/compare`, `/actuator/**` |
| `worker` | `services/worker` | `@Scheduled` опрос цен | `services/worker/Dockerfile` | `/actuator/health`, `PollScheduler` |
| `nginx` | `services/nginx` | reverse proxy, точка входа :80 | `services/nginx/Dockerfile` | `/`, `/api/` |

У каждого сервиса есть отдельная папка с `Dockerfile` и `README.md`, где описан его API:
`services/<имя>/README.md`.

## Документы защиты

- Тема и уникальность: **[docs/topic.md](docs/topic.md)**
- C4 Context / Container / Component: **[docs/c4.md](docs/c4.md)**
- Текстовый протокол проверок: **[docs/checks.md](docs/checks.md)**

## Задача и сценарий

Система следит за ценами товаров и помогает выбрать лучший вариант по цене и комплектации.
Пользователь добавляет URL, тему и целевую цену; характеристики подтягиваются из страницы
товара (`mock` — стабильные демо-значения). Воркер периодически опрашивает цену, история
сохраняется в PostgreSQL, при цене ниже порога создаётся алерт, график рисуется сервером как SVG.

Поддерживаемые темы: **ноутбуки, телефоны, наушники, полные сборки ПК**.
Сравнение берёт 2–4 товара одной темы, считает баллы по весам характеристик,
делит их на актуальную цену из карточки и выдаёт вердикт «что лучше за эти деньги».

Демо-сценарий: войти под демо-аккаунтом → добавить два ноутбука → отметить их чекбоксами
→ «Сравнить выбранные» → увидеть таблицу CPU/RAM/экрана и вердикт.
Отдельно можно проверить цены: «Опросить» → алерт → график → повторный опрос в ту же минуту
не создаёт дубль.

## Архитектура

C4-схемы трёх уровней (Context, Container, Component): **[docs/c4.md](docs/c4.md)**.

Взаимодействие сервисов:

- браузер → nginx (HTTP :80);
- nginx → app (`/` → Thymeleaf UI, `/api/` → REST JSON);
- app и worker → PostgreSQL через JDBC/JPA;
- migrate → PostgreSQL через Flyway SQL один раз при старте;
- app и worker → провайдер цены через интерфейс `PriceProvider` (mock или HTML/JSON-LD);
- app → провайдер характеристик через интерфейс `SpecProvider`: `mock` или `html`
  (JSON-LD `additionalProperty`, meta-теги, эвристики по тексту страницы);
- `CategoryCatalog` хранит веса характеристик для четырёх тем, `ComparisonService` считает
  «баллы за 1000 ₽» и сохраняет сравнение в таблице `comparisons`;
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
# {"status":"UP","groups":["liveness","readiness"]}
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

### 5. Сравнение товаров

```powershell
$body = '{"productIds":["<id-1>","<id-2>"],"name":"Laptops: best for price"}'
curl.exe -s -X POST http://localhost/api/compare -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

UI: главная → фильтр по теме → чекбоксы 2–4 товаров → «Сравнить выбранные».

Скриншоты для отчёта: страница «Отслеживаемые товары» с фильтром, ценой и алертом,
страница товара с SVG-графиком, страница `/compare` с вердиктом,
вывод `docker compose ps -a` и вывод `curl /actuator/health`.

## Демо через curl

```powershell
$token = (curl.exe -s -X POST http://localhost/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"demo@example.com","password":"demopass123"}' | ConvertFrom-Json).access_token

$product = curl.exe -s -X POST http://localhost/api/products `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d '{"url":"https://shop.example/product/42","title":"Кроссовки X","alertThreshold":9500,"category":"headphones"}' | ConvertFrom-Json

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
| `POST` | `/api/auth/login` | токен `access_token` (регистрации нет) |
| `GET` | `/api/categories` | темы и веса характеристик |
| `GET/POST` | `/api/products` | список с пагинацией и фильтром `?category=pc` / создать |
| `GET/PATCH/DELETE` | `/api/products/{id}` | получить / изменить / удалить |
| `POST` | `/api/products/{id}/poll` | опросить цену |
| `GET` | `/api/products/{id}/snapshots` | история снимков |
| `GET` | `/api/products/{id}/stats` | агрегация по дням |
| `GET` | `/api/stats/top-drops` | максимальное падение цены |
| `GET` | `/api/alerts` | непрочитанные алерты |
| `POST` | `/api/alerts/{id}/read` | отметить прочитанным |
| `POST` | `/api/compare` | сравнить 2–4 товара одной темы |
| `GET` | `/api/comparisons` | сохранённые сравнения |

## Важное

- `docker compose down` сохраняет данные; `down -v` удаляет volume.
- Секреты только в `.env`; `.env` в `.gitignore`.
- JWT для браузера лежит в httpOnly-cookie; для API можно передавать `Authorization: Bearer`.
- `SPEC_PROVIDER=mock` (по умолчанию) даёт стабильные характеристики для демо;
  `SPEC_PROVIDER=html` пробует достать их из JSON-LD/meta/текста страницы — сайты могут блокировать ботов.
- В сравнении участвуют только товары одной темы; доступно от 2 до 4 товаров.

## Локальная разработка (опционально)

Нужны JDK 17 и Maven:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/pricetracker"
$env:DATABASE_USER="ptuser"
$env:DATABASE_PASSWORD="..."
$env:JWT_SECRET="..."
mvn spring-boot:run
```
