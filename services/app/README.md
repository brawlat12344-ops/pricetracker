# Сервис `app`

**Ответственность:** REST API + пользовательский интерфейс. Единственный сервис, доступный пользователю через nginx.

**Профиль запуска:** `app` (зашит в `Dockerfile` в `ENTRYPOINT`).

## API

### HTML/UI

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/`, `/?page=N` | Список товаров с пагинацией и алерты |
| `GET` | `/login`, `/register` | Страницы входа и регистрации |
| `POST` | `/login`, `/register`, `/logout` | Формы, httpOnly-cookie `pricetracker_token` |
| `GET` | `/product/{id}` | Страница товара с SVG-графиком |
| `POST` | `/web/products` | Добавить товар |
| `POST` | `/web/products/{id}/poll` | Опросить цену |
| `POST` | `/web/products/{id}/delete` | Удалить товар |
| `POST` | `/web/alerts/{id}/read` | Отметить алерт прочитанным |

### REST JSON (`/api/**`)

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/auth/register` | Регистрация → `access_token` |
| `POST` | `/api/auth/login` | Вход → `access_token` |
| `GET` | `/api/products` | Список, `page`, `size` |
| `POST` | `/api/products` | Создать товар |
| `GET` | `/api/products/{id}` | Получить товар |
| `PATCH` | `/api/products/{id}` | Изменить `title`, `alertThreshold`, `active` |
| `DELETE` | `/api/products/{id}` | Удалить товар |
| `POST` | `/api/products/{id}/poll` | Опросить цену |
| `GET` | `/api/products/{id}/snapshots` | История снимков |
| `GET` | `/api/products/{id}/stats` | Агрегация по дням |
| `GET` | `/api/stats/top-drops` | Максимальное падение цены |
| `GET` | `/api/alerts` | Непрочитанные алерты |
| `POST` | `/api/alerts/{id}/read` | Прочитать алерт |

### Операционный API

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/actuator/health` | Healthcheck Docker и nginx |
| `GET` | `/actuator/info` | Информация о приложении |

**Зависимости:** `db` (JDBC/JPA), `migrate` (должен завершиться), провайдер цен.
**Порт:** 8080 внутри сети `appnet`.
