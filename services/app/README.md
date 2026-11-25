# Сервис `app`

**Ответственность:** REST API + пользовательский интерфейс + сравнение товаров по темам.
Единственный сервис, доступный пользователю через nginx.

**Профиль запуска:** `app` (зашит в `Dockerfile` в `ENTRYPOINT`).

## API

### HTML/UI

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/`, `/?page=N&category=laptop` | Список товаров с пагинацией, фильтром темы и алертами |
| `GET` | `/login`, `/register` | Страницы входа и регистрации |
| `POST` | `/login`, `/register`, `/logout` | Формы, httpOnly-cookie `pricetracker_token` |
| `GET` | `/product/{id}` | Страница товара с SVG-графиком |
| `GET` | `/compare?ids=<uuid>&ids=<uuid>` | Сравнение 2–4 товаров одной темы |
| `POST` | `/web/products` | Добавить товар (поля `url`, `title`, `alertThreshold`, `category`) |
| `POST` | `/web/products/{id}/poll` | Опросить цену |
| `POST` | `/web/products/{id}/delete` | Удалить товар |
| `POST` | `/web/alerts/{id}/read` | Отметить алерт прочитанным |

### REST JSON (`/api/**`)

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/auth/register` | Регистрация → `access_token` |
| `POST` | `/api/auth/login` | Вход → `access_token` |
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

### Операционный API

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/actuator/health` | Healthcheck Docker и nginx |
| `GET` | `/actuator/info` | Информация о приложении |

**Зависимости:** `db` (JDBC/JPA), `migrate` (должен завершиться), провайдер цен и провайдер характеристик (`SPEC_PROVIDER=mock|html`).
**Порт:** 8080 внутри сети `appnet`.
