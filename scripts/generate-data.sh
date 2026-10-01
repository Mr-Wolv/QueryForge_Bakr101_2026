#!/usr/bin/env bash
# Deterministic dataset generator (spec command shape): ./scripts/generate-data.sh 1000000
# Writes benchmark/seed/products_seed42.csv (seed=42, row i identical at any N).
set -euo pipefail
cd "$(dirname "$0")/.."
exec python scripts/generate-data.py "${1:-10000}" --csv benchmark/seed/products_seed42.csv
