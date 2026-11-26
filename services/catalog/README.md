# catalog — каталоги, скоринг и отчёты

Порт `8081` (проброс `CATALOG_PORT`), профиль `catalog`, точка входа
`com.example.pricetracker.catalog.CatalogApplication`.

## Ответственность

- `CategoryCatalog` — четыре темы (ноутбуки, телефоны, наушники, сборки ПК) и веса атрибутов.
- `CheapSearchService` + `SameConfigMatcher` + `ComparisonService` — строгая проверка
  «та же или лучшая конфигурация дешевле» и оценки по шкале 0–10.
- `BuildService` / `PartCatalog` — совместимая сборка, обоснование, инструкция, апгрейд.
- `LaptopService` — подбор 3–5 моделей, бенчмарки, цены и прогноз на 3–5 лет.
- `ResponseCache` — TTL 12 минут (`CACHE_TTL_MINUTES`), до 200 записей.

## Внутренний API

Доступен только в сети `appnet` (не проксируется nginx), без Spring Security:

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/internal/categories` | справочник тем |
| `POST` | `/internal/cheaper` | поиск дешевле |
| `POST` | `/internal/build` | отчёт по сборке |
| `POST` | `/internal/laptops` | отчёт по ноутбукам |
| `GET` | `/internal/info` | состояние цепочки до price |
| `GET` | `/actuator/health`, `/actuator/metrics` | health и метрики кэша |

## Межсервисные вызовы

`PriceClient` ходит в `price:8082` (`PRICE_URL`):

| Путь | Назначение |
|---|---|
| `POST /internal/offers` | карточки магазинов по ссылке или короткому запросу |
| `GET /internal/info` | проверка доступности price |

## Зависимости

`price` (здоров). База данных не нужна: пользователи и JWT живут в gateway.
