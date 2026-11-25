CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    url             TEXT          NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    alert_threshold NUMERIC(12,2) NOT NULL CHECK (alert_threshold > 0),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_user_url UNIQUE (user_id, url)
);

CREATE TABLE price_snapshots (
    id           BIGSERIAL     PRIMARY KEY,
    product_id   UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    price        NUMERIC(12,2) NOT NULL CHECK (price > 0),
    currency     VARCHAR(3)    NOT NULL DEFAULT 'RUB',
    captured_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    period_start TIMESTAMPTZ   NOT NULL,
    provider     VARCHAR(32)   NOT NULL,
    CONSTRAINT uq_snapshot_product_period UNIQUE (product_id, period_start)
);

-- B-tree: выборка всех снимков одного товара за период
CREATE INDEX idx_snapshots_product_time
    ON price_snapshots (product_id, captured_at DESC);

-- BRIN: дешёвый индекс по времени для append-only данных
CREATE INDEX idx_snapshots_captured_brin
    ON price_snapshots USING brin (captured_at) WITH (pages_per_range = 64);

CREATE TABLE alerts (
    id          BIGSERIAL     PRIMARY KEY,
    product_id  UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    snapshot_id BIGINT        NOT NULL REFERENCES price_snapshots(id) ON DELETE CASCADE,
    price       NUMERIC(12,2) NOT NULL,
    threshold   NUMERIC(12,2) NOT NULL,
    is_read     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_unread
    ON alerts (created_at DESC) WHERE NOT is_read;

CREATE INDEX idx_alerts_product_unread
    ON alerts (product_id, created_at DESC) WHERE NOT is_read;
