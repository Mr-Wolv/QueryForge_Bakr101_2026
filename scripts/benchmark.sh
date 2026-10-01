#!/usr/bin/env bash
# Convenience wrapper: run a k6 scenario from the repo root so summary JSON
# lands in benchmark/results/. All args are forwarded to k6; bare VAR=value
# args are automatically converted to `-e VAR=value` flags.
#
# Usage: ./scripts/benchmark.sh search RATE=10 DUR=60s OUT=qf001-x
set -euo pipefail
cd "$(dirname "$0")/.."

SCENARIO="${1:?usage: benchmark.sh [search|pagination|concurrency|point] [k6 args...]}"

K6_BIN="${K6_BIN:-tools/k6/k6.exe}"
command -v k6 >/dev/null 2>&1 && K6_BIN="k6"

shift
args=()
for a in "$@"; do
  if [[ "$a" == -* ]]; then
    args+=("$a")
  else
    args+=("-e" "$a")
  fi
done

exec "$K6_BIN" run "${args[@]}" "benchmark/k6/${SCENARIO}.js"
