-- Keep in sync with db/migrations/V1__create_products.sql.
-- Baseline schema: no secondary indexes beyond the primary key.
CREATE TABLE IF NOT EXISTS products (
    id             BIGSERIAL PRIMARY KEY,
    sku            VARCHAR(128)  NOT NULL,
    name           VARCHAR(512)  NOT NULL,
    category_id    BIGINT        NOT NULL,
    price          NUMERIC(12,2) NOT NULL,
    stock_quantity INT           NOT NULL DEFAULT 0,
    status         VARCHAR(32)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS categories (
    id   BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL
);

-- Reference data matching the generator's 5 category buckets
-- (kept in sync with db/migrations/V1__create_products.sql).
INSERT INTO categories (id, name) VALUES
    (1, 'electronics'),
    (2, 'clothing'),
    (3, 'home'),
    (4, 'sports'),
    (5, 'books')
ON CONFLICT (id) DO NOTHING;
