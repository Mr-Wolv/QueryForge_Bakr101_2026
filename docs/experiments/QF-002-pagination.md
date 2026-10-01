# QF-002 — OFFSET vs KEYSET pagination at increasing depth

**Workload:** same filter as QF-001, 50 rows/page, measured at result-set depths
0 / 1,000 / 10,000 / 50,000 rows. Keyset cursors are derived in `setup()` from the offset endpoint
so both strategies are measured at the exact same logical position in the result set.
**Dataset:** 1,000,000 rows (seed=42), **indexed state** (`idx_products_search` present).
**Load:** 20 req/s constant arrival, 60 s per mode.

## Implementation

- OFFSET: `GET /api/products?page=N&size=50` — Spring Data `Pageable` (LIMIT 51 + `count(*)`).
- KEYSET: `GET /api/products/keyset?cursor=...&size=50` —
  [ProductKeysetRepository](../../src/main/java/com/bakr/queryforge/repo/ProductKeysetRepository.java),
  opaque base64url cursor of `(created_at, id)`, predicate
  `created_at < :cv OR (created_at = :cv AND id < :cid)`, no COUNT, no OFFSET.

## Results (p50 / p95 per depth)

| Depth (rows skipped) | OFFSET p50 | OFFSET p95 | KEYSET p50 | KEYSET p95 |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 117 ms | 187 ms | 68 ms | 94 ms |
| 1,000 | 120 ms | 165 ms | 68 ms | 94 ms |
| 10,000 | 129 ms | 178 ms | 62 ms | 87 ms |
| 50,000 | **129 ms** | 173 ms | **40 ms** | 53 ms |

Keyset latency is **flat across depth** (40–68 ms); OFFSET carries a roughly constant premium
(≈ 117–129 ms p50) composed of (a) the OFFSET skip itself and (b) the mandatory `count(*)`
Spring Data issues per page — 11.4 ms at this size that keyset never pays.

The signature OFFSET degradation (latency ∝ depth) is muted here because the index makes the
filter cheap enough that walking to the offset is fast; the COUNT elimination and the removed
OFFSET walk are the measurable wins (≈ 1.7–3.2× per page). The plans:

- Deep offset: [qf002-offset-deep-explain.txt](plans/qf002-offset-deep-explain.txt)
- Keyset at the same depth: [plans/qf002-keyset-baseline-explain.txt](plans/qf002-keyset-baseline-explain.txt)

![offset vs keyset](../../benchmark/results/offset-vs-keyset.png)

## Why keyset wins

1. No `OFFSET`: the index directly seeks to the cursor position — cost is O(log n + page), not
   O(depth + page).
2. No COUNT: keyset pages don't know their total size, so the query is one index-bounded scan.
3. Deterministic order: `(created_at, id)` tiebreaking makes pages stable under concurrent inserts.

## Tradeoff

- Keyset only supports **next/previous relative to a cursor** — you cannot jump to page N.
- The cursor is opaque but tied to `(sortColumn, dir)`; changing sort mid-walk invalidates it.
- Total count (if UI needs it) must be fetched separately/cached.
