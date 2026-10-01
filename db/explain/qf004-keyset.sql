-- QF-004: keyset pagination probe at depth 50,000.
-- Cursor literal derived from the current dataset's k6 setup() cursor for depth 50000
-- (base64 "MTcwNDUwNDMyOTAwMDo0MzcxMjk=" -> epoch millis 1704504329000 = 2024-01-06T00:05:29Z, id 437129).
-- Same SQL is EXPLAINed against both index orderings (filter-first and order-first).
SELECT id, sku, name, category_id, price, stock_quantity, status, created_at, updated_at
FROM products
WHERE category_id = 1
  AND status = 'ACTIVE'
  AND price BETWEEN 100 AND 500
  AND (created_at < '2024-01-06T00:05:29Z'::timestamptz
       OR (created_at = '2024-01-06T00:05:29Z'::timestamptz AND id < 437129))
ORDER BY created_at DESC, id DESC
LIMIT 50;
