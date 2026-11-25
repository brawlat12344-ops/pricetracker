# Сервис `app`

**Ответственность:** REST API + пользовательский интерфейс + сравнение товаров по темам
+ поиск до 10 аналогов + поиск той же сборки дешевле в магазинах по одной ссылке.
Единственный сервис, доступный пользователю через nginx.

**Профиль запуска:** `app` (зашит в `Dockerfile` в `ENTRYPOINT`).

## API

### HTML/UI

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/`, `/?page=N&category=laptop` | Список товаров с пагинацией, фильтром темы и алертами |
| `GET` | `/login` | Страница входа (регистрации нет) |
| `POST` | `/login`, `/logout` | Формы, httpOnly-cookie `pricetracker_token` |
| `GET` | `/product/{id}` | Страница товара с SVG-графиком |
| `GET` | `/compare?ids=<uuid>&ids=<uuid>` | Сравнение 2–4 товаров одной темы |
| `GET` | `/analogs?url=...&category=pc&limit=10&web=false` | Поиск аналогов по одной ссылке |
| `GET` | `/cheaper?url=...&category=pc&limit=10&crossStore=true` | Поиск дешевле: та же комплектация, цена ниже |
| `POST` | `/web/products` | Добавить товар (поля `url`, `title`, `alertThreshold`, `category`) |
| `POST` | `/web/products/{id}/poll` | Опросить цену |
| `POST` | `/web/products/{id}/delete` | Удалить товар |
| `POST` | `/web/alerts/{id}/read` | Отметить алерт прочитанным |

### REST JSON (`/api/**`)

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/auth/login` | Вход → `access_token`; демо: `demo@example.com / demopass123` |
| `GET` | `/api/categories` | Темы и веса характеристик |
| `GET` | `/api/products` | Список, `page`, `size`, `category` |
| `POST` | `/api/products` | Создать товар (`category`, опционально `attributes`) |
| `GET` | `/api/products/{id}` | Получить товар |
| `PATCH` | `/api/products/{id}` | Изменить `title`, `alertThreshold`, `active` |
| `DELETE` | `/api/products/{id}` | Удалить товар |
| `POST` | `/api/products/{id}/poll` | Опросить цену |
| `GET` | `/api/products/{id}/snapshots` | История снимков |
| `GET` | `/api/products/{id}/stats` | Агрегация по дням |
| `GET` | `/api/stats/top-drops` | Максимальное падение цены |
| `GET` | `/api/alerts` | Непрочитанные алерты |
| `POST` | `/api/alerts/{id}/read` | Прочитать алерт |
| `POST` | `/api/compare` | Сравнить 2–4 товара: `productIds`, `name` |
| `GET` | `/api/comparisons` | Последние сохранённые сравнения |
| `POST` | `/api/analogs` | Аналоги по ссылке: `url`, `category`, `title`, `limit`, `web` |
| `POST` | `/api/cheaper` | Дешевле в магазинах: `url`, `category`, `title`, `limit`, `crossStore` |

### Операционный API

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/actuator/health` | Healthcheck Docker и nginx |
| `GET` | `/actuator/info` | Информация о приложении |

**Зависимости:** `db` (JDBC/JPA), `migrate` (должен завершиться), провайдер цен и провайдер характеристик (`SPEC_PROVIDER=mock|html`).
Для аналогов используется демо-каталог и, при `ANALOG_WEB_SEARCH=true` или `web=true`, веб-поиск DuckDuckGo/Bing.
Для поиска дешевле — `StoreRegistry`/`StoreOffersSource` (DNS-Shop, М.Видео, Ситилинк, Ozon,
Wildberries, Bing), `StoreProductParser`, `ConfigExtractor`, `SameConfigMatcher` и демо-фолбэк.
Ссылки в отчёте кликабельны: реальные карточки ведут на товар, демо-варианты — на поиск конфигурации
в магазине. Оба отчёта разовые и не пишутся в БД.
**Порт:** 8080 внутри сети `appnet`.
