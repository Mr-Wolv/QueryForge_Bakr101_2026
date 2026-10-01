#!/usr/bin/env bash
# Load a reproducible dataset (seed=42) into the docker compose Postgres.
# Usage: ./scripts/load-data.sh [rows]        e.g. ./scripts/load-data.sh 1000000
set -euo pipefail
cd "$(dirname "$0")/.."

ROWS="${1:-10000}"
SEED="${SEED:-42}"
CSV="benchmark/seed/products_seed${SEED}.csv"
PSQL=(docker compose exec -T db psql -U queryforge -d queryforge -v ON_ERROR_STOP=1)

echo ">> generating ${ROWS} rows (seed=${SEED})"
python scripts/generate-data.py "${ROWS}" --csv "${CSV}"

echo ">> truncating products"
"${PSQL[@]}" -c "TRUNCATE products RESTART IDENTITY;"

echo ">> COPY load (single transaction)"
"${PSQL[@]}" -c "\copy products (id, sku, name, category_id, price, stock_quantity, status, created_at, updated_at) FROM STDIN WITH (FORMAT csv, HEADER true)" < "${CSV}"

echo ">> VACUUM + ANALYZE"
"${PSQL[@]}" -c "VACUUM (ANALYZE) products;"

echo ">> row count + created_at range"
"${PSQL[@]}" -c "SELECT count(*) AS rows, min(created_at) AS min_ts, max(created_at) AS max_ts FROM products;"
