#!/usr/bin/env bash
# Apply the QF-001 optimization index manually (NOT part of baseline schema).
# Usage: ./scripts/apply-index.sh
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose exec -T db psql -U queryforge -d queryforge -v ON_ERROR_STOP=1 \
  < db/migrations/V2__idx_products_search.sql

docker compose exec -T db psql -U queryforge -d queryforge -c "ANALYZE products;"
echo ">> index applied"
