# PriceTracker — поиск той же конфигурации дешевле

![PriceTracker — тёмный баннер](docs/assets/pricetracker-banner.png)

Stack: **Java 17 + Spring Boot 3.3 + Thymeleaf + Flyway + PostgreSQL 16 + nginx**.
Python, Node.js и Next.js не используются: страницы — серверные шаблоны, анимации — CSS.

Приложение делает одну вещь: по одной ссылке на товар в DNS-Shop, М.Видео, Ситилинке,
Ozon, Wildberries или любом другом магазине ищет **ту же или лучшую конфигурацию дешевле**.
Найденные карточки — разовый отчёт; в отслеживание они не добавляются.

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
SPEC_PROVIDER=mock
APP_PORT=80
"@ | Set-Content .env -Encoding utf8

docker compose up --build -d
docker compose ps -a
```

Открыть: **http://localhost** → `/cheaper`.

**Регистрация закрыта.** Вход по готовым аккаунтам из миграции `V3__demo_users.sql`:
- `demo@example.com` / `demopass123`
- `student@example.com` / `qwerty123`

## Быстрый запуск одним кликом (Windows)

Двойной клик по `PriceTracker.exe` в корне проекта: проверит Docker Desktop, выполнит
`docker compose up -d`, дождётся `{"status":"UP"}` и откроет `http://localhost`.
Без браузера: `.\PriceTracker.exe --no-open`. Исходник: `tools/launcher/README.md`.

## Сервисы

| Сервис | Папка | Ответственность | API |
|---|---|---|---|
| `db` | `services/db` | PostgreSQL 16, volume `db_data` | SQL :5432 |
| `migrate` | `services/migrate` | Flyway V1–V4, one-shot | CLI / Flyway |
| `app` | `services/app` | REST API + Thymeleaf UI + поиск дешевле | `/api/**`, `/cheaper`, `/actuator/**` |
| `nginx` | `services/nginx` | reverse proxy, точка входа :80 | `/`, `/api/` |

У каждого сервиса есть `Dockerfile` и `README.md` в `services/<имя>/`.

## Сценарий

1. Войти под демо-аккаунтом.
2. Вставить ссылку на сборку ПК/ноутбук/телефон/наушники, выбрать тему и число предложений (1–10).
3. Выбрать «Искать во всех магазинах» или «Только магазин из ссылки».
4. Получить вердикт, список дешевле и наглядную матрицу характеристик: для ПК это CPU, GPU,
   RAM, SSD, БП, охлаждение, ОС и корпус — с иконками, весами и шкалами баллов.

`StoreRegistry` определяет магазин по хосту. `StoreOffersSource` запрашивает поисковую страницу
магазина и (при `crossStore=true`) других магазинов, запасной канал — Bing.
`StoreProductParser` разбирает JSON-LD/DOM, `ConfigExtractor` вытаскивает CPU/GPU/RAM/SSD/БП/
охлаждение/ОС/корпус, а `SameConfigMatcher` пропускает только не худшую комплектацию
со строго меньшей ценой.
Характеристики исходника не подменяются характеристиками найденной карточки — иначе любая
страница считалась бы «той же сборкой».

Если сайт блокирует бота (HTTP 401/429, SPA-оболочка), подключается `DemoCheaperSource`
с искусственно заниженными ценами и честным предупреждением в `warnings`. Такие карточки
помечены бейджем `ДЕМО-ЦЕНА` в UI и полем `"demo": true` в API; ссылка ведёт на реальный поиск
конфигурации в магазине, где цену нужно проверить вручную.

Скоринг переиспользует `ComparisonService.evaluate`: веса из `CategoryCatalog` для четырёх тем
(`laptop`, `phone`, `headphones`, `pc`), результат — «баллов за 1000 ₽». Поиск ничего не пишет в БД.

## Документы защиты

- Тема и уникальность: **[docs/topic.md](docs/topic.md)**
- C4 Context / Container / Component: **[docs/c4.md](docs/c4.md)**
- Текстовый протокол проверок: **[docs/checks.md](docs/checks.md)**

## Проверка результата

```powershell
docker compose ps -a
# pt-app Up (healthy), pt-db Up (healthy), pt-migrate Exited (0), pt-nginx Up (healthy)

curl.exe http://localhost/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

Вход и поиск через API:

```powershell
$token = (curl.exe -s -X POST http://localhost/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"demo@example.com","password":"demopass123"}' | ConvertFrom-Json).access_token

$body = '{"url":"https://www.dns-shop.ru/product/8f1a2b3c4d5e/pc-rtx-4070-core-i5-32gb/","category":"pc","title":"сборка ПК Core i5 RTX 4070 32 ГБ","limit":10,"crossStore":true}'
curl.exe -s -X POST http://localhost/api/cheaper -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

В ответе: `store`, `sourcePrice`, `sourceSpecs`, `items` (`store`, `title`, `url`, `price`,
`discountPercent`, `attributes`, `score`, `valueScore`, `demo`), `winnerId`, `verdict`,
`warnings`, `foundOffers`, `rejectedOffers`. В список попадают только предложения с ценой
строго ниже исходной и комплектацией не хуже. Ограничение — 10 карточек.

### Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/` | редирект на `/cheaper` |
| `GET` | `/login`, `/cheaper` | UI входа и поиска |
| `POST` | `/login`, `/logout` | формы входа/выхода, httpOnly-cookie |
| `POST` | `/api/auth/login` | токен `access_token` (регистрации нет) |
| `GET` | `/api/categories` | темы и веса характеристик |
| `POST` | `/api/cheaper` | найти ту же конфигурацию дешевле в магазинах |
| `GET` | `/actuator/health` | healthcheck Docker и nginx |

## Важное

- `docker compose down` сохраняет данные; `down -v` удаляет volume `db_data`.
- Секреты только в `.env`; `.env` в `.gitignore`.
- JWT для браузера лежит в httpOnly-cookie; для API — `Authorization: Bearer`.
- `SPEC_PROVIDER=mock` даёт стабильные демо-характеристики; `SPEC_PROVIDER=html` пробует
  JSON-LD/meta/эвристики со страницы. Сайты могут блокировать ботов — тогда срабатывает фолбэк.
- Старые таблицы отслеживания (`products`, `price_snapshots`, `alerts`, `comparisons`) удалены
  миграцией `V4__drop_tracking_tables.sql`; в БД остаются только `users` и Flyway-история.
- Для стабильных реальных цен нужен официальный API магазина или договор с агрегатором.

## Локальная разработка (опционально)

Нужны JDK 17 и Maven:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/pricetracker"
$env:DATABASE_USER="ptuser"
$env:DATABASE_PASSWORD="..."
$env:JWT_SECRET="..."
mvn spring-boot:run
```
