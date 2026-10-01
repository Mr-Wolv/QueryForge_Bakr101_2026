-- QueryForge schema.
-- NOTE: products intentionally has NO secondary indexes beyond the PK (Stage 0 baseline).
-- Search-tuning indexes are introduced as measured experiments
-- (see db/migrations/V2__idx_products_search.sql and docs/experiments/).
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

-- Static reference data matching the generator's 5 category buckets.
INSERT INTO categories (id, name) VALUES
    (1, 'electronics'),
    (2, 'clothing'),
    (3, 'home'),
    (4, 'sports'),
    (5, 'books')
ON CONFLICT (id) DO NOTHING;
