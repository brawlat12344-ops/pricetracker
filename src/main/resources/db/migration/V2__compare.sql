-- Категории и характеристики товаров для сравнения
ALTER TABLE products
    ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'laptop',
    ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_products_user_category ON products (user_id, category);

-- Сохранённые сравнения (2–4 товара)
CREATE TABLE comparisons (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255)  NOT NULL,
    category    VARCHAR(32)   NOT NULL,
    product_ids JSONB         NOT NULL,
    winner_id   UUID,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_comparison_size CHECK (jsonb_array_length(product_ids) BETWEEN 2 AND 4)
);

CREATE INDEX idx_comparisons_user_created ON comparisons (user_id, created_at DESC);
