# Scripts

Every step of the reproduction trail, in execution order.

## Docker-first setup on Windows, macOS, and Linux

The full app can be started without installing a host JDK, Node.js, Python, k6, or Bash:

```text
docker compose --profile app up --build -d --wait
docker compose port frontend 80
docker compose exec db psql -U queryforge -d queryforge -v ON_ERROR_STOP=1 -f /workspace/db/migrations/V1__create_products.sql
docker compose run --rm data-generator 1000000
docker compose exec db psql -U queryforge -d queryforge -v ON_ERROR_STOP=1 -f /workspace/scripts/load-data.sql
```

Open the frontend address from `docker compose port frontend 80`. Compose dynamically assigns
available loopback host ports for the frontend, API, and database; use `docker compose port backend
8080` and `docker compose port db 5432` to discover the latter addresses. The generator runs in a
disposable Python container and the database loads the generated CSV from a shared read-only mount.
Loading replaces existing product rows. `docker compose --profile app down` stops the stack; add
`-v` only to discard the database volume.

The benchmark harness below remains Bash-based because it orchestrates host tools such as k6 and
Maven. Its default host endpoints are API `:8080` and database `:5433`; set `QF_API_PORT=8080` and
`QF_DB_PORT=5433` in `.env` before starting Compose when using this flow. On Windows, use Git Bash
or WSL for those commands; the Docker-first app and dataset setup above use the same commands in
PowerShell, Command Prompt, macOS Terminal, and Linux shells.

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
