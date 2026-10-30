TRUNCATE products RESTART IDENTITY;

COPY products (
    id,
    sku,
    name,
    category_id,
    price,
    stock_quantity,
    status,
    created_at,
    updated_at
)
FROM '/seed/products_seed42.csv'
WITH (FORMAT csv, HEADER true);

SELECT setval(
    pg_get_serial_sequence('products', 'id'),
    COALESCE(MAX(id), 1),
    MAX(id) IS NOT NULL
)
FROM products;

VACUUM (ANALYZE) products;
