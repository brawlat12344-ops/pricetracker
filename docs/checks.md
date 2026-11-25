# Протокол проверки результата

Текстовый вывод команд для защиты. Скриншоты можно сделать тех же самых мест.

## 1. Проверка с нуля

```powershell
cd "C:\Users\user\Desktop\project DevOPs\pricetracker"
docker compose up --build -d
```

Ожидаемый финал сборки:

```text
✔ Image pricetracker-db        Built
✔ Image pricetracker-migrate   Built
✔ Image pricetracker-app       Built
✔ Image pricetracker-worker    Built
✔ Image pricetracker-nginx     Built
✔ Network pricetracker_appnet  Created
✔ Volume pricetracker_db_data  Created
```

## 2. Состояние сервисов

```powershell
docker compose ps -a
```

```text
NAME         IMAGE                  STATUS                      PORTS
pt-app       pricetracker-app       Up (healthy)                8080/tcp
pt-db        pricetracker-db        Up (healthy)                0.0.0.0:5432->5432/tcp
pt-migrate   pricetracker-migrate   Exited (0) 30 seconds ago
pt-nginx     pricetracker-nginx     Up (healthy)                0.0.0.0:80->80/tcp
pt-worker    pricetracker-worker    Up (healthy)                8080/tcp
```

`Exited (0)` у `pt-migrate` — успешная одноразовая миграция.

## 3. Healthcheck через nginx

```powershell
curl.exe http://localhost/actuator/health
```

```json
{"status":"UP","groups":["liveness","readiness"]}
```

## 4. Миграции в базе

```powershell
docker exec -it pt-db psql -U ptuser -d pricetracker -c "SELECT version, description, success FROM flyway_schema_history;"
```

```text
 version | description | success
---------+-------------+---------
 1       | init        | t
 2       | compare     | t
 3       | demo_users  | t
```

`V2__compare.sql` добавляет `products.category`, `products.attributes` (JSONB)
и таблицу `comparisons` для сохранённых сравнений. `V3__demo_users.sql` создаёт два демо-аккаунта.

## 5. Вход и сценарий через curl

```powershell
$base = "http://localhost/api"

$token = (curl.exe -s -X POST "$base/auth/login" `
  -H "Content-Type: application/json" `
  -d '{"email":"demo@example.com","password":"demopass123"}' | ConvertFrom-Json).access_token
```

Регистрации нет: аккаунты создаются миграцией `V3__demo_users.sql`. Второй вариант входа —
`student@example.com` / `qwerty123`. Ответ содержит `access_token` — токен сохранён в `$token`.

```powershell
$product = curl.exe -s -X POST "$base/products" `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{"url":"https://shop.example/product/42","title":"Кроссовки","alertThreshold":9500,"category":"headphones"}' | ConvertFrom-Json
```

```json
{"id":"...","title":"Кроссовки","url":"https://shop.example/product/42","category":"headphones","attributes":{"driver":"40mm dynamic","anc":"active","battery_h":"40","connectivity":"bt 5.2","weight_kg":"0.28"},"alertThreshold":9500.00,"active":true,"latestPrice":null,"unreadAlerts":0}
```

Список доступных тем:

```powershell
curl.exe -s "$base/categories" -H "Authorization: Bearer $token"
```

```json
[{"key":"laptop","label":"Ноутбуки",...},{"key":"phone","label":"Телефоны",...},{"key":"headphones","label":"Наушники",...},{"key":"pc","label":"Сборка ПК",...}]
```

Первый опрос:

```powershell
curl.exe -s -X POST "$base/products/$($product.id)/poll" -H "Authorization: Bearer $token"
```

```json
{"snapshotId":1,"productId":"...","price":9053.00,"provider":"mock","created":true,"alertCreated":true,"periodStart":"..."}
```

Повторный опрос в ту же минуту:

```powershell
curl.exe -s -X POST "$base/products/$($product.id)/poll" -H "Authorization: Bearer $token"
```

```json
{"snapshotId":1,"productId":"...","price":9053.00,"provider":"mock","created":false,"alertCreated":false,"periodStart":"..."}
```

`created:false` — доказательство идемпотентности.

Алерты:

```powershell
curl.exe -s "$base/alerts" -H "Authorization: Bearer $token"
```

```json
{"items":[{"id":1,"title":"Кроссовки","price":9053.00,"threshold":9500.00,"read":false}],"total":1,"page":0,"pageSize":20}
```

Статистика по дням:

```powershell
curl.exe -s "$base/products/$($product.id)/stats?days=7" -H "Authorization: Bearer $token"
```

```json
[{"day":"2026-11-25","minPrice":9053.00,"maxPrice":9053.00,"avgPrice":9053.00,"samples":1}]
```

## 6. Сравнение товаров (4 темы)

Добавляем два ноутбука — характеристики подтянутся автоматически:

```powershell
$a = curl.exe -s -X POST "$base/products" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{"url":"https://shop.example/laptop-alpha","title":"Laptop Alpha","alertThreshold":90000,"category":"laptop"}' | ConvertFrom-Json

$b = curl.exe -s -X POST "$base/products" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{"url":"https://shop.example/laptop-beta","title":"Laptop Beta","alertThreshold":90000,"category":"laptop"}' | ConvertFrom-Json
```

```json
{"id":"...","title":"Laptop Alpha","category":"laptop","attributes":{"cpu":"Ryzen 5 7640U","gpu":"RTX 4070","ram_gb":"32","storage_gb":"1024","screen":"OLED 120Hz","battery_mah":"7500","weight_kg":"1.35"},"alertThreshold":90000.00,"active":true,"latestPrice":null,"unreadAlerts":0}
```

Сравниваем:

```powershell
$body = '{"productIds":["' + $a.id + '","' + $b.id + '"],"name":"Laptops: best for price"}'
curl.exe -s -X POST "$base/compare" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

```json
{
  "comparisonId": "...",
  "category": "laptop",
  "categoryLabel": "Ноутбуки",
  "items": [
    {"title":"Laptop Alpha","price":9061.00,"score":69.2,"valueScore":7.64,"missingAttributes":[]},
    {"title":"Laptop Beta","price":9040.00,"score":67.7,"valueScore":7.49,"missingAttributes":[]}
  ],
  "winnerId": "...",
  "verdict": "Лучшее соотношение «комплектующие за цену»: Laptop Alpha — 69.2 баллов при 9061.00 ₽ (показатель 7.64 балла за 1000 ₽)."
}
```

Alpha дороже на 21 ₽, но выигрывает по комплектации (Ryzen 5 + RTX 4070 + 32 ГБ + OLED
против Core i7 + RTX 4060 + 16 ГБ) — именно это и показывает вердикт «что лучше за цену».

Сохранённые сравнения и веб-страница:

```powershell
curl.exe -s "$base/comparisons" -H "Authorization: Bearer $token"
```

Открыть в браузере: `http://localhost/compare?ids=<id-alpha>&ids=<id-beta>`.

## 7. Поиск до 10 аналогов по одной ссылке

```powershell
$body = '{"url":"https://shop.example/pc/my-build","category":"pc","title":"сборка ПК RTX 4070","limit":10,"web":false}'
curl.exe -s -X POST "$base/analogs" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

Ожидаемый вывод (сокращённо):

```json
{
  "sourceUrl": "https://shop.example/pc/my-build",
  "category": "pc",
  "categoryLabel": "Сборка ПК",
  "source": "демо-каталог",
  "items": [
    {"title":"Мой ПК","price":109600.00,"score":65.8,"valueScore":0.60},
    {"title":"Core i5 + RTX 4060 · старт","price":89900.00,"score":50.1,"valueScore":0.56},
    {"title":"Ryzen 5 + RTX 4060 · бюджет","price":92500.00,"score":49.2,"valueScore":0.53}
  ],
  "winnerId": "...",
  "verdict": "Лучшее за цену: Мой ПК — 0.60 балла за 1000 ₽ ... Исходная ссылка занимает 1 место.",
  "warnings": ["Провайдер цены работает в демо-режиме: цена исходной ссылки оценена по характеристикам."]
}
```

`items` содержит ровно 10 карточек: исходная ссылка плюс лучшие демо-сборки.
Все найденные карточки остаются разовым отчётом — в список отслеживаемых товаров не попадают.

Чтобы попробовать реальный поиск, замени `"web":false` на `"web":true` (или включи чекбокс
на странице и установи `ANALOG_WEB_SEARCH=true`). Источник сначала опрашивает DuckDuckGo HTML,
при блокировке — Bing. Ссылки, у которых не нашлись цена и характеристики, пропускаются,
а причина пишется в `warnings`; свободные места занимают карточки демо-каталога.

UI: `http://localhost/analogs` (ссылка «Поиск аналогов» в шапке).

## 8. Поиск той же сборки дешевле в магазинах

```powershell
$body = '{"url":"https://www.dns-shop.ru/product/8f1a2b3c4d5e/pc-rtx-4070-core-i5-32gb/","category":"pc","title":"сборка ПК Core i5 RTX 4070 32 ГБ","limit":10,"crossStore":true}'
curl.exe -s -X POST "$base/cheaper" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

Ожидаемый вывод (сокращённо):

```json
{
  "sourceUrl": "https://www.dns-shop.ru/product/8f1a2b3c4d5e/pc-rtx-4070-core-i5-32gb/",
  "store": "DNS-Shop",
  "sourcePrice": 146500.00,
  "items": [
    {"store":"Демо-Ситилинк","title":"core i5 / rtx 4070 / 64 ГБ / БП 850 Вт","price":104020.00,"score":80.1,"valueScore":0.77},
    {"store":"Демо-М.Видео","title":"Core i7-14700K / rtx 4070 / 32 ГБ","price":109880.00,"score":70.0,"valueScore":0.66}
  ],
  "winnerId": "...",
  "verdict": "Дешевле и не хуже по комплектации: ... на 29% ниже исходных 146500.00 ₽ ...",
  "warnings": [
    "Характеристики исходной страницы не прочитаны: HTTP 401 ...",
    "DNS-Shop: поиск недоступен — HTTP 401",
    "Демо-варианты сгенерированы для демонстрации: цены ниже исходной заданы искусственно."
  ],
  "foundOffers": 3,
  "rejectedOffers": 3
}
```

`items` содержит до 10 карточек. Каждая проходит проверку `SameConfigMatcher`: параметры
из исходника не подменяются, поэтому «дешевле» означает ту же или лучшую комплектацию.
`crossStore:false` ограничивает поиск магазином из ссылки. На практике DNS отвечает `401`,
Ситилинк — `429`, М.Видео — пустую SPA-оболочку, поэтому честный фолбэк — демо-карточки
с предупреждением в `warnings`.

UI: `http://localhost/cheaper` (ссылка «Найти дешевле» в шапке).

## 9. Логи воркера

```powershell
docker compose logs -f worker
```

```text
worker_cycle_done products=1 created=1 duplicates=0 failed=0
```

## 10. Что фотографировать

1. Вывод `docker compose ps -a`.
2. Вывод `curl.exe http://localhost/actuator/health`.
3. Главная страница: фильтр по теме, таблица товаров, чекбоксы и цена.
4. Страница товара: SVG-график и последние снимки.
5. Вывод повторного `/poll` с `"created":false`.
6. Страница `/compare`: таблица характеристик, баллы и вердикт «Лучшее за цену».
7. Вывод `POST /api/compare` с `verdict` и `valueScore`.
8. Страница `/analogs` с десятью карточками, таблицей и вердиктом.
9. Вывод `POST /api/analogs` с `items`, `winnerId` и `warnings`.
10. Страница `/cheaper` с предложениями дешевле, скидками и вердиктом.
11. Вывод `POST /api/cheaper` с `store`, `sourcePrice`, `items` и `warnings`.
