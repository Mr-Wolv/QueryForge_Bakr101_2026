#!/usr/bin/env bash
# NFR5 — lightweight performance-regression check for the critical filtered-search query.
#
# Runs a short k6 burst against a RUNNING app + DB, then fails if p95 exceeds a generous
# threshold (default 1000 ms) so noisy CI runners don't flake. Intended for the indexed
# state; tune THRESHOLD_MS per environment.
#
# Usage: ./scripts/perf-regression.sh [THRESHOLD_MS]
set -euo pipefail
cd "$(dirname "$0")/.."

THRESHOLD_MS="${1:-1000}"
OUT="perf-regression-check"
DUR="${DUR:-20s}"
RATE="${RATE:-10}"

K6_BIN="${K6_BIN:-tools/k6/k6.exe}"
command -v k6 >/dev/null 2>&1 && K6_BIN="k6"

echo ">> perf regression check: filtered search @ ${RATE} req/s for ${DUR} (threshold p95 < ${THRESHOLD_MS} ms)"
"$K6_BIN" run -e DATASET=1M -e RATE="${RATE}" -e DUR="${DUR}" -e OUT="${OUT}" benchmark/k6/search.js > /dev/null 2>&1

python - "$THRESHOLD_MS" <<'EOF'
import json, sys
threshold = float(sys.argv[1])
v = json.load(open("benchmark/results/perf-regression-check.json"))["metrics"]["http_req_duration"]["values"]
p95 = v["p(95)"]
print(f"p95 = {p95:.1f} ms (threshold {threshold:.0f} ms)")
if p95 > threshold:
    print("FAIL: p95 exceeded threshold")
    sys.exit(1)
print("OK")
EOF
