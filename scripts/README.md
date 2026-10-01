# Scripts

Every step of the reproduction trail, in execution order.

| Script | Purpose |
| --- | --- |
| [generate-data.sh](generate-data.sh) | Deterministic dataset generator — `./scripts/generate-data.sh 1000000` writes `benchmark/seed/products_seed42.csv` (seed=42, row *i* identical at any N) |
| [load-data.sh](load-data.sh) | Generate + `COPY`-load into the compose Postgres, single transaction, `VACUUM (ANALYZE)` |
| [reset-db.sh](reset-db.sh) | Truncate products + reset `pg_stat_statements` (optionally `--seed-rows N` to reload) |
| [apply-index.sh](apply-index.sh) | Apply the QF-001 optimization index **manually** — keeps the no-index baseline reproducible |
| [explain.sh](explain.sh) | `EXPLAIN (ANALYZE, BUFFERS)` on a SQL file → e.g. `./scripts/explain.sh db/explain/qf001-baseline.sql` |
| [benchmark.sh](benchmark.sh) | Run a k6 scenario from the repo root — `./scripts/benchmark.sh search RATE=10 DUR=60s OUT=run1` (bare `VAR=value` args become `-e` flags; auto-detects `k6` on PATH or `tools/k6/`) |
| [perf-regression.sh](perf-regression.sh) | NFR5 check: short filtered-search burst, fails if p95 > threshold (default 1000 ms, CI-tolerant) |
| [make-graphs.py](make-graphs.py) | Rebuild the PNG graphs from `benchmark/results/*.json` (needs `pip install matplotlib`) |
| [generate-data.py](generate-data.py) | Python core of the generator (called by generate-data.sh) |

## Typical flow

```bash
docker compose up -d db
./scripts/load-data.sh 1000000      # generate + load + analyze (1M rows, seed=42)
mvn spring-boot:run                 # API on :8080

./scripts/benchmark.sh search RATE=10 DUR=60s OUT=qf001-baseline-search-1M-r10
./scripts/apply-index.sh            # the measured change
./scripts/benchmark.sh search RATE=10 DUR=60s OUT=qf001-indexed-search-1M-r10

./scripts/perf-regression.sh        # p95 regression gate
python scripts/make-graphs.py       # refresh graphs
```
