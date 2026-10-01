# QueryForge — Conclusions

Answers to the questions this project set out to investigate. All numbers from the 1M-row
(seed=42) experiments detailed in [benchmarks.md](benchmarks.md) and the four experiment reports.

## 1. Which query became the bottleneck, and why?

The **filtered search** (`category + status + price range + ORDER BY created_at DESC + OFFSET page`)
at 1M rows. With no secondary index, PostgreSQL planned a **Parallel Seq Scan** reading all 1M rows
(114 MB) per request — 911K of them discarded by the filter — plus a second full-table pass for the
envelope `count(*)`. Uncontended that is ~165 ms; under any real load it turns into queueing:
at 10 req/s offered, p95 = **8.18 s**, at 30 req/s p50 = **19 s**, at 100 req/s the pool times out.

The scaling experiment ([QF-000](experiments/QF-000-dataset-scaling.md)) shows *why* this hides at
small scale: the identical code runs at p50 9.9 ms on 10K rows and 37.2 ms on 100K rows (both fit
in cache) and only collapses at 1M — which is exactly why the dataset had to be large and
reproducible for any conclusion here to be meaningful.

## 2. What did the planner do, and what changed it?

- **Before**: `Parallel Seq Scan → Sort (top-N heapsort) → Limit`; `Buffers: read=11784` per page.
- **After** `CREATE INDEX ... (category_id, status, price, created_at DESC)`:
  `Parallel Bitmap Heap Scan` fed by `Bitmap Index Scan on idx_products_search` — 88,913 rows
  located with 3 index buffers, `Heap Blocks: exact=4294`; the `count(*)` became an
  **Index Only Scan** (79 ms → 11.4 ms).

The planner did exactly what the evidence suggested it would once given a structure shaped like the
workload: equality columns, then range, then sort. No planner hints, no rewriting of the query.

## 3. How did latency change?

| Workload | Before | After | Factor |
| --- | ---: | ---: | ---: |
| Filtered search p50 (10 req/s) | 3.69 s | 92 ms | **~40×** |
| Filtered search p95 (10 req/s) | 8.18 s | 130 ms | **~63×** |
| Envelope COUNT (DB time) | 79 ms | 11.4 ms | ~7× |
| Page 50K deep (keyset vs OFFSET p50) | 129 ms | 40 ms | ~3.2× |

Every latency claim is stated only for its measured mechanism — see the pagination note in
[QF-002](experiments/QF-002-pagination.md#why-keyset-wins): with the filter-first index, keyset
removes the OFFSET walk and the per-page COUNT; it is **not** a proven direct cursor seek.

## 4. What happened under concurrency?

With the index in place the system hits a clean **throughput plateau at ~36–43 req/s** from 10 to
200 VUs, latency growing linearly (252 ms → 5.34 s p50) with **zero errors**. Little's law confirms
the mechanism: 10 Hikari connections × ~260 ms per request ≈ 38 req/s. The bottleneck moved from
the database's access path to the **connection pool** — the next justified change is pool sizing /
deployment parallelism, *not* another index.

## 5. What tradeoffs did the optimizations introduce?

- **Composite index**: +~60 MB storage; every write maintains 4 indexed columns; helps only this
  workload family (e.g. `sort=price` still scans). Kept out of V1 on purpose so the baseline stays
  reproducible. The order-first alternative (`created_at` before `price`) was built, measured and
  **kept as a candidate** — see [QF-004](experiments/QF-004-index-ordering.md).
- **Keyset pagination**: flat, cheap, deterministic — but restricted to `sort=createdAt` (the
  cursor encodes `(created_at, id)`; other sorts are a 400), no page jumping, cursor tied to
  `dir`, and total count needs a separate path.

## 6. What would we do next (evidence permitting)?

1. Raise Hikari pool (or scale app replicas) and re-run the QF-003 ladder — the plateau should move
   to the next resource (likely DB CPU).
2. Order-first index `(category_id, status, created_at DESC, price)` — built and measured in
   [QF-004](experiments/QF-004-index-ordering.md): it won every read query on this dataset
   (keyset becomes a true index walk, 24.7 vs 36.2 ms p50 at 50K) but optimizes a non-bottleneck
   and carries an unmeasured narrow-prefix risk, so it stays a documented candidate, not a swap.
3. `sort=price` support via a second, narrower index *if* telemetry shows demand.
4. Covering index (`INCLUDE`) if the Bitmap Heap recheck shows up again at higher cache pressure.
5. pg_stat_statements is enabled and captures per-query means — a regression-watch script over it
   would be the natural CI extension.

## The one-sentence version

At 1M rows, a workload-shaped composite index and cursor pagination turned a service that collapsed
under 10 req/s (p95 8.18 s) into one that serves 30 req/s at p95 252 ms with flat deep-page latency
— at the cost of ~60 MB and some write overhead, with the next bottleneck (the 10-connection pool)
already measured and named.

---

## CV bullet (earned by the measurements above)

> **QueryForge — PostgreSQL Performance Engineering Service**
> Java 25 | Spring Boot | PostgreSQL 17 | Docker | k6
> • Built a reproducible performance-testing environment for a PostgreSQL-backed REST service using deterministic seed-controlled datasets up to 1M records and k6 workloads from single-user to 200 concurrent users.
> • Diagnosed query bottlenecks with `EXPLAIN (ANALYZE, BUFFERS)` and `pg_stat_statements` — a parallel seq scan discarding 911K rows per request plus a full-table COUNT — then validated a workload-shaped composite index through repeatable before/after benchmarks.
> • Reduced p95 for filtered search from **8,180 ms to 130 ms (~63×)** at 10 req/s (sustaining 30 req/s at p95 252 ms), demonstrated flat keyset vs OFFSET pagination (**40 ms vs 129 ms** p50 at 50K-row depth), and identified the 10-connection pool as the next bottleneck via a measured 10→200-VU throughput plateau.
