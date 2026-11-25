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
```

`Exited (0)` у `pt-migrate` — успешная одноразовая миграция. `pt-worker` в системе нет.

## 3. Healthcheck через nginx

```powershell
curl.exe http://localhost/actuator/health
```

```json
{"status":"UP","groups":["liveness","readiness"]}
```

## 4. Миграции и схема БД

```powershell
docker exec pt-db psql -U ptuser -d pricetracker -c "SELECT version, description, success FROM flyway_schema_history ORDER BY version;"
docker exec pt-db psql -U ptuser -d pricetracker -c "\dt"
```

```text
 version | description          | success
---------+----------------------+---------
 1       | init                 | t
 2       | compare              | t
 3       | demo users           | t
 4       | drop tracking tables | t
```

В базе остались только таблицы `users` и `flyway_schema_history`. Старые таблицы отслеживания
удалены: приложение работает только с разовым поиском дешевле.

## 5. Вход

```powershell
$base = "http://localhost/api"

$token = (curl.exe -s -X POST "$base/auth/login" `
  -H "Content-Type: application/json" `
  -d '{"email":"demo@example.com","password":"demopass123"}' | ConvertFrom-Json).access_token
```

Регистрации нет: аккаунты создаёт `V3__demo_users.sql`. Второй вариант —
`student@example.com` / `qwerty123`. Ответ содержит `access_token`, он сохранён в `$token`.

Список тем:

```powershell
curl.exe -s "$base/categories" -H "Authorization: Bearer $token"
```

```json
[{"key":"laptop","label":"Ноутбуки",...},{"key":"phone","label":"Телефоны",...},{"key":"headphones","label":"Наушники",...},{"key":"pc","label":"Сборка ПК",...}]
```

## 6. Поиск той же сборки дешевле

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
    {"store":"М.Видео","title":"core i5 / rtx 4070 / 64 ГБ / БП 850 Вт","price":104020.00,"score":80.1,"valueScore":0.77,"demo":true},
    {"store":"DNS-Shop","title":"Core i7-14700K / rtx 4070 / 32 ГБ","price":109880.00,"score":70.0,"valueScore":0.66,"demo":true}
  ],
  "winnerId": "...",
  "verdict": "Лучшее предложение: ... на 29% ниже исходных 146500.00 ₽ ...",
  "warnings": [
    "Характеристики исходной страницы не прочитаны: HTTP 401 ...",
    "DNS-Shop: поиск недоступен — HTTP 401",
    "У демо-вариантов цены искусственно занижены: ссылка открывает реальный поиск магазина ..."
  ],
  "foundOffers": 3,
  "rejectedOffers": 4
}
```

`items` содержит до 10 карточек. У каждой карточки есть `attributes` и `attributeScores` по восьми
параметрам ПК (CPU, GPU, RAM, SSD, БП, охлаждение, ОС, корпус): на их основе UI рисует матрицу
с иконками, весами и шкалами. Каждая карточка проходит проверку `SameConfigMatcher`: параметры
из исходника не подменяются, поэтому «дешевле» означает ту же или лучшую комплектацию.
`crossStore:false` ограничивает поиск магазином из ссылки. DNS отвечает `401`, Ситилинк — `429`,
М.Видео отдаёт пустую SPA-оболочку, поэтому фолбэк — демо-карточки с предупреждением.

## 7. UI

```powershell
curl.exe -I http://localhost/
curl.exe -s http://localhost/cheaper -H "Cookie: pricetracker_token=$token"
```

- `http://localhost/` → `302` на `/cheaper`;
- `http://localhost/cheaper` → HTTP 200, форма, адаптивная навигация, анимации;
- после поиска: вердикт, предупреждения и сравнение комплектаций; отдельного списка предложений нет;
- названия в заголовках столбцов таблицы — кликабельные ссылки `target="_blank"`: демо-варианты
  открывают поиск конфигурации в DNS, М.Видео, Ситилинке, Ozon или Wildberries;
- бейджи `ДЕМО-ЦЕНА` и `ЛУЧШЕЕ ЗА ЦЕНУ` остаются в таблице;
- старых разделов `/products`, `/analogs`, `/compare` нет — они возвращают `404`;
- фон страниц — `#000000` с тонкими белыми изолиниями: `contours-wide.svg` для альбомного
  окна, `contours-tall.svg` для портретного; генератор — `tools/contours.html`.

## 8. Что фотографировать

1. Вывод `docker compose ps -a` (четыре сервиса, без воркера).
2. Вывод `curl.exe http://localhost/actuator/health`.
3. Вывод `POST /api/cheaper` с `store`, `sourcePrice`, `items` и `warnings`.
4. Страницу `/cheaper` с формой, вердиктом, скидками и таблицей «баллов за 1000 ₽».
5. Вывод `docker exec pt-db psql ... \dt` — только `users` и Flyway-история.
