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
{"status":"UP"}
```

## 4. Миграции в базе

```powershell
docker exec -it pt-db psql -U ptuser -d pricetracker -c "SELECT version, description, success FROM flyway_schema_history;"
```

```text
 version | description | success
---------+-------------+---------
 1       | init        | t
```

## 5. Регистрация и сценарий через curl

```powershell
$base = "http://localhost/api"

$token = (curl.exe -s -X POST "$base/auth/register" `
  -H "Content-Type: application/json" `
  -d '{"email":"test@mail.ru","password":"qwerty123"}' | ConvertFrom-Json).access_token
```

Ответ содержит `access_token` — токен сохранён в переменной `$token`.

```powershell
$product = curl.exe -s -X POST "$base/products" `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{"url":"https://shop.example/product/42","title":"Кроссовки","alertThreshold":9500}' | ConvertFrom-Json
```

```json
{"id":"...","title":"Кроссовки","url":"https://shop.example/product/42","alertThreshold":9500.00,"active":true,"latestPrice":null,"unreadAlerts":0}
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

## 6. Логи воркера

```powershell
docker compose logs -f worker
```

```text
worker_cycle_done products=1 created=1 duplicates=0 failed=0
```

## 7. Что фотографировать

1. Вывод `docker compose ps -a`.
2. Вывод `curl.exe http://localhost/actuator/health`.
3. Главная страница: таблица товаров, цена и плашка алерта.
4. Страница товара: SVG-график и последние снимки.
5. Вывод повторного `/poll` с `"created":false`.
