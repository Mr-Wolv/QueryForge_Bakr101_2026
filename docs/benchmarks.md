# QueryForge — Benchmark log

Every experiment follows the same contract:

> **Scenario → Dataset → Query → Baseline metrics → `EXPLAIN (ANALYZE, BUFFERS)` → Observation →
> Change → Re-measure → Result → Explanation → Tradeoff.**

Raw k6 metric JSON lives in [benchmark/results/](../benchmark/results/), execution plans in
[docs/experiments/plans/](experiments/plans/).

## Experiment index

| ID | Question | Status | Report |
| --- | --- | --- | --- |
| QF-000 | How does baseline latency scale with dataset size? Where is the point-lookup floor? | done | [QF-000-dataset-scaling.md](experiments/QF-000-dataset-scaling.md) |
| QF-001 | Why is filtered search slow at 1M rows, and what does a workload-shaped index buy? | done | [QF-001-baseline.md](experiments/QF-001-baseline.md) |
| QF-002 | How do OFFSET and KEYSET pagination behave as page depth grows? | done | [QF-002-pagination.md](experiments/QF-002-pagination.md) |
| QF-003 | What limits the backend under increasing concurrency? | done | [QF-003-concurrency.md](experiments/QF-003-concurrency.md) |

## Benchmark matrix

| Dataset | Query | Variant | Concurrency | Metrics |
| ------: | --- | --- | ---: | --- |
| 10K | Filtered search | Baseline | 10 req/s offered | p50 9.9 ms / p95 13.5 ms / p99 17.1 ms, 10 RPS |
| 100K | Filtered search | Baseline | 10 req/s offered | p50 37.2 ms / p95 50.5 ms / p99 84.8 ms, 10 RPS |
| 1M | Filtered search | Baseline (no index) | 10 req/s offered | p50 3.69 s / p95 8.18 s / p99 8.93 s, 8.6 RPS |
| 1M | Point lookup | PK index | 10 req/s | p50 4.8 ms / p95 8.0 ms (Workload A floor) |
| 1M | Filtered search | Indexed | 10 req/s offered | p50 92 ms / p95 130 ms / p99 151 ms, 10 RPS |
| 1M | Filtered search | Indexed | 30 req/s offered | p50 131 ms / p95 252 ms / p99 349 ms, 29.9 RPS |
| 1M | Pagination depth 0–50K | OFFSET | 20 req/s | p50 117→129 ms per depth |
| 1M | Pagination depth 0–50K | KEYSET | 20 req/s | p50 68→40 ms per depth, flat |
| 1M | Filtered search | Indexed | 10→200 VUs | flat ~36–43 RPS plateau (see QF-003) |

Baseline at higher offered rates collapsed entirely (26 s p50 at 100 req/s, pool timeouts) —
recorded in QF-001 rather than the matrix because the comparison at matched 10 req/s is the
controlled one.

## Reproducing

```bash
docker compose up -d db                          # Postgres 17 + pg_stat_statements (host port 5433)
./scripts/load-data.sh 1000000                   # deterministic dataset, seed=42
mvn spring-boot:run                              # API on :8080

# baseline run  (schema has NO secondary indexes yet)
tools/k6/k6.exe run -e DATASET=1M -e RATE=10 -e DUR=60s -e OUT=qf001-baseline-search-1M-r10 benchmark/k6/search.js

./scripts/apply-index.sh                         # QF-001 change
tools/k6/k6.exe run -e DATASET=1M -e RATE=10 -e DUR=60s -e OUT=qf001-indexed-search-1M-r10 benchmark/k6/search.js

tools/k6/k6.exe run -e MODE=offset -e RATE=20 -e DUR=60s -e OUT=qf002-offset-1M benchmark/k6/pagination.js
tools/k6/k6.exe run -e MODE=keyset -e RATE=20 -e DUR=60s -e OUT=qf002-keyset-1M benchmark/k6/pagination.js
tools/k6/k6.exe run -e VUS=100 -e DUR=45s -e OUT=qf003-vus100 benchmark/k6/concurrency.js
tools/k6/k6.exe run -e RATE=10 -e DUR=30s -e OUT=qf000a-point-lookup-1M benchmark/k6/point.js
```

## Regression awareness (NFR5)

`./scripts/perf-regression.sh [threshold_ms]` runs a short filtered-search burst against a running
app and fails if p95 exceeds a generous threshold (default 1000 ms) — light enough for CI, loose
enough that a busy shared runner won't flake it. It measured p95 = 8.5 ms against a 500 ms
threshold on the reference machine in the indexed 1M state.

k6 resolves output paths from the CWD — run it from the repository root.
On non-Windows, use `k6` instead of `tools/k6/k6.exe` (see `scripts/README.md` for setup).

## Measurement notes / honesty

- Single Windows host runs Postgres (Docker), the JVM, and k6 — absolute numbers are
  machine-specific; the **deltas between states** are the deliverable.
- Every scenario: 0 errors unless stated, warm ~5 s, k6 `http_req_duration` (end-to-end).
- Plans captured with `EXPLAIN (ANALYZE, BUFFERS)`; dataset regenerated from seed=42 before runs.
- **Process note:** an intermediate result set was captured against a test-truncated table
  (250 rows) and discarded; the definitive suite above was re-run on a freshly loaded 1M dataset
  in one session, same order, same cache conditions.
