# Сервис `app`

**Ответственность:** REST API + пользовательский интерфейс + поиск той же сборки дешевле
в магазинах по одной ссылке. Единственный сервис, доступный пользователю через nginx.

**Профиль запуска:** `app` (зашит в `Dockerfile` в `ENTRYPOINT`).

## API

### HTML/UI

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/` | редирект на `/cheaper` |
| `GET` | `/login` | страница входа (регистрации нет) |
| `POST` | `/login`, `/logout` | формы, httpOnly-cookie `pricetracker_token` |
| `GET` | `/cheaper?url=...&category=pc&limit=10&crossStore=true` | поиск дешевле: та же комплектация, цена ниже |

### REST JSON (`/api/**`)

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/auth/login` | вход → `access_token`; демо: `demo@example.com / demopass123` |
| `GET` | `/api/categories` | темы и веса характеристик |
| `POST` | `/api/cheaper` | дешевле в магазинах: `url`, `category`, `title`, `limit`, `crossStore` |

### Операционный API

| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/actuator/health` | healthcheck Docker и nginx |
| `GET` | `/actuator/info` | информация о приложении |

**Зависимости:** `db` (только таблица `users` для входа), `migrate` (должен завершиться).
Поиск дешевле строится на `StoreRegistry`/`StoreOffersSource` (DNS-Shop, М.Видео, Ситилинк,
Ozon, Wildberries, Bing), `StoreProductParser`, `ConfigExtractor`, `SameConfigMatcher`,
`DemoCheaperSource` и `ComparisonService.evaluate`. Для ПК учитываются восемь параметров:
CPU, GPU, RAM, SSD, БП (`psu_w`), охлаждение (`cooling`), ОС (`os`) и корпус (`case`);
каждый имеет вес, иконку и балл, который визуализируется шкалой в таблице. Ссылки кликабельны:
реальные карточки ведут на товар, демо-варианты — на поиск конфигурации в магазине.
Отчёт разовый и не пишется в БД.
**Порт:** 8080 внутри сети `appnet`.
