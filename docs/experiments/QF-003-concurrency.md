# QF-003 — Concurrency ladder (closed-model load)

**Workload:** QF-001 filtered search, page 0, size 50 — **indexed state**, 1M rows.
**Load:** constant-VUs (true concurrent users), 10 → 25 → 50 → 100 → 200, 45 s per level.
**Config under test:** Spring Boot 3.5 (Tomcat, default 200 threads), Hikari pool **10 connections**
(deliberately left at defaults for Stage 0–3).

## Results

| VUs | RPS | p50 | p95 | p99 | max | Errors |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 38.6 | 252 ms | 367 ms | 442 ms | 557 ms | 0 |
| 25 | 35.9 | 676 ms | 877 ms | 1.11 s | 1.29 s | 0 |
| 50 | 42.6 | 1.15 s | 1.38 s | 1.51 s | 2.33 s | 0 |
| 100 | 39.6 | 2.47 s | 3.06 s | 3.45 s | 5.63 s | 0 |
| 200 | 36.3 | 5.34 s | 6.48 s | 8.92 s | 11.7 s | 0 |

![throughput plateau](../../benchmark/results/throughput-plateau.png)

## Reading the ladder

Throughput is **flat at ~36–43 req/s from 10 to 200 users** while latency grows almost linearly
with concurrency (252 ms → 5.34 s p50). This is the textbook signature of a **saturated connection
pool**: the database work per request (measured in QF-001) is only ~90–140 ms of DB time, but only
10 requests can execute concurrently; everyone else waits in the Hikari queue. Little's law:
10 connections / ~0.26 s per request ≈ 38 req/s — exactly the plateau observed.

There is **no database thrashing and no errors** — the system degrades gracefully into queueing.
That also means the next lever is *not* another index; it is the pool size / deployment
parallelism (a deliberate Stage-4+ follow-up, not applied here so the numbers above stay
comparable with earlier experiments).

## Baseline vs indexed under load (same 10 req/s offered)

| State | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Baseline (no index) | 3.69 s | 8.18 s | 8.93 s |
| Indexed | 92 ms | 130 ms | 151 ms |

Even before touching the pool, the index multiplied the sustainable concurrency: DB time per
request fell ~10×, so the same 10 connections can serve ~10× the offered DB work.
