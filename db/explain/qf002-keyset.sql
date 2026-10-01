SELECT id, sku, name, category_id, price, stock_quantity, status, created_at, updated_at
FROM products
WHERE category_id = 1
  AND status = 'ACTIVE'
  AND price BETWEEN 100 AND 500
  AND (created_at < '2024-09-02T00:53:21Z'::timestamptz
       OR (created_at = '2024-09-02T00:53:21Z'::timestamptz AND id < 50000))
ORDER BY created_at DESC, id DESC
LIMIT 50;
