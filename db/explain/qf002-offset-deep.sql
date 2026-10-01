SELECT id, sku, name, category_id, price, stock_quantity, status, created_at, updated_at
FROM products
WHERE category_id = 1
  AND status = 'ACTIVE'
  AND price BETWEEN 100 AND 500
ORDER BY created_at DESC
LIMIT 50 OFFSET 50000;
