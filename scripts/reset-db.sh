#!/usr/bin/env bash
# Reset benchmark state: empty products, reset pg_stat_statements.
# Usage: ./scripts/reset-db.sh [--seed-rows N]
set -euo pipefail
cd "$(dirname "$0")/.."

PSQL=(docker compose exec -T db psql -U queryforge -d queryforge -v ON_ERROR_STOP=1)

"${PSQL[@]}" -c "TRUNCATE products RESTART IDENTITY;"
"${PSQL[@]}" -c "SELECT pg_stat_statements_reset();"
echo ">> products truncated, pg_stat_statements reset"

if [ "${1:-}" = "--seed-rows" ]; then
  ./scripts/load-data.sh "${2:?usage: reset-db.sh --seed-rows N}"
fi
