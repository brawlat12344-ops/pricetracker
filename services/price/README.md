# price — поиск цен и карточек магазинов

Порт `8082` (проброс `PRICE_PORT`), профиль `price`, точка входа
`com.example.pricetracker.price.PriceApplication`.

## Ответственность

- **Единственный сервис, который делает HTTP-запросы к сайтам магазинов** и поисковику.
- `StoreRegistry` выбирает магазин по хосту ссылки; `StoreProductParser` разбирает
  JSON-LD/DOM-карточки; `ConfigExtractor` вытаскивает CPU/GPU/RAM/SSD/БП/охлаждение/ОС/корпус.
- `SearchQueryBuilder` строит короткий поисковый запрос, чтобы магазины отдавали результаты.
- `StoreBreaker`: 3 подряд ошибки → цепь открыта 60 с → один пробный запрос.
  Пустая SPA-оболочка считается метрикой `empty`, но цепь не размыкает.
- `DemoCheaperSource` добавляет честно помеченные демо-цены, когда сайт блокирует парсер.
- Метрики Micrometer: `pt.store.requests`, `pt.store.latency`, `pt.store.circuit`,
  `pt.store.breaker.open`.

## Внутренний API

Доступен только в сети `appnet` (не проксируется nginx), без Spring Security:

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/internal/offers` | поиск карточек по `sourceUrl`/`title` или готовому `query` |
| `GET` | `/internal/info` | состояние сервиса для проверки цепочки |
| `GET` | `/actuator/health`, `/actuator/metrics` | health и метрики магазинов |

## Граница

`price` не знает про каталоги, скоринг и пользователей: классы `builder`, `compare`, `laptop`, `repo`
и `web` не попадают в его компонентное сканирование. Результат — список карточек и `warnings`,
которые уже интерпретирует `catalog`.
