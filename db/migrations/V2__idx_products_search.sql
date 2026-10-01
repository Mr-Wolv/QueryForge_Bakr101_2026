-- QF-001 (Phase 5 optimization) — applied MANUALLY via scripts/apply-index.sh,
-- NOT automatically at DB init, so the baseline experiment stays reproducible.
--
-- Rationale: filtered-search workload is
--   WHERE category_id = ? AND status = ? AND price BETWEEN ? AND ?
--   ORDER BY created_at DESC LIMIT 50
-- Index order = equality cols, then range col, then sort col.
CREATE INDEX IF NOT EXISTS idx_products_search
    ON products (category_id, status, price, created_at DESC);
