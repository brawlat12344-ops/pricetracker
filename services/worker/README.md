# Сервис `worker`

**Ответственность:** фоновый опрос цен отдельным процессом. Не обслуживает пользовательские запросы.

**Профиль запуска:** `worker` (зашит в `Dockerfile` в `ENTRYPOINT`).

## API

### Операционный HTTP API

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/actuator/health` | Healthcheck контейнера |
| `GET` | `/actuator/info` | Информация о процессе |

Порт 8080 доступен только внутри сети `appnet`, наружу не проброшен.

### Внутренний API планировщика

| Компонент | Контракт |
|---|---|
| `PollScheduler.pollAll()` | `@Scheduled(fixedDelayString = "${pricetracker.poll-interval-ms}")` |
| `PricingService.poll(productId)` | Вернуть `PollResponse(snapshotId, price, created, alertCreated, periodStart)` |
| `PriceProvider.fetch(url)` | Вернуть `PriceResult(price, currency, title)` |

### Поток данных

1. Читает активные товары из `products` через `ProductRepository`.
2. Для каждого вызывает `PricingService.poll`.
3. Пишет снимок через `INSERT ... ON CONFLICT DO NOTHING`.
4. При `цена <= порог` создаёт алерт.
5. Логирует `worker_cycle_done products=... created=... duplicates=... failed=...`.

Ошибка одного товара не останавливает цикл — она попадает в счётчик `failed`.

**Зависимости:** `db`, `migrate`, провайдер цен.
