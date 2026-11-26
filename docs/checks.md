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

$token = (curl.exe -s -X POST "$base/api/auth/login" `
  -H "Content-Type: application/json" `
  -d '{"email":"demo@example.com","password":"demopass123"}' | ConvertFrom-Json).access_token
```

Регистрации нет: аккаунты создаёт `V3__demo_users.sql`. Второй вариант —
`student@example.com` / `qwerty123`. Ответ содержит `access_token`, он сохранён в `$token`.

Список тем:

```powershell
curl.exe -s "$base/api/categories" -H "Authorization: Bearer $token"
```

```json
[{"key":"laptop","label":"Ноутбуки",...},{"key":"phone","label":"Телефоны",...},{"key":"headphones","label":"Наушники",...},{"key":"pc","label":"Сборка ПК",...}]
```

## 6. Поиск той же сборки дешевле

```powershell
$body = '{"url":"https://www.dns-shop.ru/product/8f1a2b3c4d5e/pc-rtx-4070-core-i5-32gb/","category":"pc","title":"сборка ПК Core i5 RTX 4070 32 ГБ","limit":10,"crossStore":true}'
curl.exe -s -X POST "$base/api/cheaper" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

Ожидаемый вывод (сокращённо):

```json
{
  "sourceUrl": "https://www.dns-shop.ru/product/8f1a2b3c4d5e/pc-rtx-4070-core-i5-32gb/",
  "store": "DNS-Shop",
  "sourcePrice": 146500.00,
  "items": [
    {"store":"М.Видео","title":"core i5 / rtx 4070 / 64 ГБ / БП 850 Вт","price":104020.00,"score":8.0,"valueScore":10.0,"demo":true},
    {"store":"DNS-Shop","title":"Core i7-14700K / rtx 4070 / 32 ГБ","price":109880.00,"score":7.0,"valueScore":9.3,"demo":true}
  ],
  "winnerId": "...",
  "verdict": "М.Видео: 104 020 ₽ (−29% к 146 500 ₽). Конфигурация 8.0/10 · цена 10.0/10. Подходит 2, отклонено 4.",
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

## 7. Локальная сборка ПК/ноутбука

```powershell
$body = '{"category":"pc","purpose":"game","priority":"performance","wishes":"","region":"Москва, Россия","budget":120000}'
curl.exe -s -X POST "$base/api/build" -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d $body
```

Ожидаемый вывод (сокращённо):

```json
{
  "category": "pc",
  "categoryLabel": "Сборка ПК",
  "purpose": "Игры",
  "priority": "Производительность",
  "region": "Москва, Россия",
  "budget": 120000,
  "parts": [
    {"type":"cpu","label":"CPU · Процессор","name":"Ryzen 5 7600","score10":7.6,
     "price":21000,"reason":"...","compatibility":"...",
     "offers":[{"store":"Демо-каталог","price":21000,"url":"https://www.dns-shop.ru/search/?q=...",
                "availability":"Наличие не указано — проверить","demo":true,"promo":false}]}
  ],
  "totalPrice": 113000.00,
  "budgetDelta": 7000.00,
  "demoPrice": true,
  "score": 7.2,
  "verdict": "Сборка: 113 000 ₽ (−7 000 ₽ к бюджету). Оценка 7.2/10. ...",
  "rationale": ["..."], "assemblySteps": ["..."], "bottlenecks": ["..."],
  "alternative": {"title":"Альтернативная сборка (+12%)","totalPrice":131000.00,"score":8.0},
  "pricedAt": "2026-11-26",
  "warnings": ["По компоненту «...» проверяемые цены не найдены: добавлены демо-предложения. ..."]
}
```

`BuildService` распределяет бюджет по компонентам, проверяет сокет, чипсет, память, TDP,
форм-фактор, длину GPU, высоту кулера, запас БП и баланс CPU/GPU; превышение бюджета более
чем на 3% запрещено. `PartPriceService` ищет по 2–3 предложения на каждую деталь параллельно,
с короткими таймаутами. `score` и `score10` лежат в диапазоне 0–10. Если сайт блокирует парсер
или найдена посторонняя карточка, добавляются демо-предложения с бейджем `ДЕМО` и ссылкой
на реальный поиск. Страница с тем же результатом:
`/cheaper?mode=build&category=pc&purpose=game&priority=performance&budget=120000&region=Moscow`.
Для ноутбука: `category=laptop&purpose=study&priority=compact&budget=90000`
→ 7 деталей, 84 500 ₽, 8.8/10, альтернатива 96 000 ₽.

## 8. UI

```powershell
curl.exe -I http://localhost/
curl.exe -s http://localhost/cheaper -H "Cookie: pricetracker_token=$token"
```

- `http://localhost/` → `302` на `/cheaper`;
- `http://localhost/cheaper` → HTTP 200, две формы, адаптивная навигация, анимации;
- после поиска: вердикт, предупреждения и сравнение комплектаций; отдельного списка предложений нет;
- после `mode=build`: таблица «Компонент | Модель | Магазин | Цена | Наличие | Источник» (2–3 строки на деталь),
  итог vs бюджет, обоснование, инструкция сборки, узкие места и альтернативная сборка;
- названия в заголовках столбцов таблицы — кликабельные ссылки `target="_blank"`: демо-варианты
  открывают поиск конфигурации в DNS, М.Видео, Ситилинке, Ozon или Wildberries;
- бейджи `ДЕМО-ЦЕНА` и `ЛУЧШЕЕ ЗА ЦЕНУ` остаются в таблице;
- старых разделов `/products`, `/analogs`, `/compare` нет — они возвращают `404`;
- фон страниц — сплошной цвет (`--bg`) и лёгкий CSS-узор: точки в светлой теме, диагональные
  штрихи в тёмной; тяжёлых SVG-изолиний и `background-attachment: fixed` нет;
- кнопка темы в правом верхнем углу переключает `data-theme`, сохраняет выбор в `localStorage`
  и учитывает системную тему при первом визите; переходы 200 мс через CSS-переменные.

## 9. Что фотографировать

1. Вывод `docker compose ps -a` (четыре сервиса, без воркера).
2. Вывод `curl.exe http://localhost/actuator/health`.
3. Вывод `POST /api/cheaper` с `store`, `sourcePrice`, `items` и `warnings`.
4. Страницу `/cheaper` с формой, коротким вердиктом, скидками и 10-балльной таблицей оценок.
5. Вывод `POST /api/build` или страницу `/cheaper?mode=build...` с таблицей предложений по деталям,
   инструкцией сборки и альтернативной конфигурацией.
6. Вывод `docker exec pt-db psql ... \dt` — только `users` и Flyway-история.
