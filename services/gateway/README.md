# gateway — пользовательский фасад

Порт `8080` (проброс `GATEWAY_PORT`), профиль `gateway`, точка входа
`com.example.pricetracker.gateway.GatewayApplication`.

## Ответственность

- Thymeleaf-страница `/cheaper` с двумя режимами (поиск дешевле и конфигуратор).
- Вход по демо-аккаунтам: форма `/login`, httpOnly-cookie и `POST /api/auth/login` (JWT).
- REST-фасад `/api/**` и публичный healthcheck `/actuator/health`.
- Единственный контейнер со Spring Security; внутренние сервисы её не поднимают.

## Межсервисные вызовы

`CatalogClient` (RestClient, таймауты 5/45 с) ходит в `catalog:8081` (`CATALOG_URL`):

| Путь | Назначение |
|---|---|
| `GET /internal/categories` | темы и веса атрибутов |
| `POST /internal/cheaper` | поиск той же конфигурации дешевле |
| `POST /internal/build` | сборка ПК/ноутбука |
| `POST /internal/laptops` | сравнение моделей ноутбуков |
| `GET /internal/info` | живая проверка цепочки gateway → catalog → price |

`GET /api/topology` показывает эту цепочку и используется в smoke-тестах.

## Зависимости

`db` (здоров) + `migrate` (успешно завершён) + `catalog` (здоров). Наружу доступен только через nginx.
