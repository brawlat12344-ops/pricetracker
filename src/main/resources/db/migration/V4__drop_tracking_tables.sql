-- Приложение больше не отслеживает товары: остаётся только разовый поиск дешевле.
-- V1/V2/V3 оставляем как историю схемы, чтобы не ломать checksum уже применённых миграций.
DROP TABLE IF EXISTS comparisons;
DROP TABLE IF EXISTS alerts;
DROP TABLE IF EXISTS price_snapshots;
DROP TABLE IF EXISTS products;
