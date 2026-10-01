#!/usr/bin/env bash
# Run EXPLAIN (ANALYZE, BUFFERS) on a SQL file.
# Usage: ./scripts/explain.sh db/explain/qf001-baseline.sql [--json]
set -euo pipefail
cd "$(dirname "$0")/.."

FILE="${1:?usage: explain.sh FILE.sql [--json]}"
FMT="TEXT"
[ "${2:-}" = "--json" ] && FMT="JSON"

{ echo "EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT ${FMT})"; cat "${FILE}"; } | \
  docker compose exec -T db psql -U queryforge -d queryforge -v ON_ERROR_STOP=1
