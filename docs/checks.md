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

## 7. Логи воркера

```powershell
docker compose logs -f worker
```

```text
worker_cycle_done products=1 created=1 duplicates=0 failed=0
```

## 8. Что фотографировать

1. Вывод `docker compose ps -a`.
2. Вывод `curl.exe http://localhost/actuator/health`.
3. Главная страница: фильтр по теме, таблица товаров, чекбоксы и цена.
4. Страница товара: SVG-график и последние снимки.
5. Вывод повторного `/poll` с `"created":false`.
6. Страница `/compare`: таблица характеристик, баллы и вердикт «Лучшее за цену».
7. Вывод `POST /api/compare` с `verdict` и `valueScore`.
