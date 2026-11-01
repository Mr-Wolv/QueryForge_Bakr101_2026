# QueryForge — Live Bug Hunt Ledger

Session started 2026-11-01. Live UI under test: `http://127.0.0.1:4200` (Angular dev server from this
checkout, proxying `/api` to the host API on `http://127.0.0.1:8080`). Browser: Chromium driven through
the desktop Browser panel; every defect below was reproduced in the running application.

> Roadmap to a defensible "done" (scored ≈ 80%, with the weighted breakdown and exit criteria):
> [`CONVERGENCE_PLAN.md`](./CONVERGENCE_PLAN.md).

Environment under test at session start:

| Item | Value |
| --- | --- |
| Branch / commit | `main` @ `05d91bc` + 5 modified / 2 new files from a prior session (preserved, untouched) |
| Backend | compose `backend` on `127.0.0.1:8081`; a separate host API on `127.0.0.1:8080` is what the UI proxies to |
| Frontend | Angular dev server `:4200`; nginx container on `:8082` |
| Database | PostgreSQL (compose, host port 56672) — `rowCount: 0` |
| k6 | UP via `tools\k6\k6.exe` |
| Security profile | `QF_SPRING_PROFILE=default` — **authentication disabled**, `operator: true` |

## Summary

| Metric | Count |
| --- | --- |
| Total findings | 7 |
| Confirmed defects | 7 |
| Critical | 0 |
| High | 1 |
| Medium | 4 |
| Low | 2 |
| Fixed and verified in the live UI | 4 |
| Fixed, awaiting live verification | 0 |
| Deferred | 1 |
| Open, not yet fixed | 2 |
| Not reproducible | 0 |
| Environment / deployment gaps (not app defects) | 1 |

## Defect index

| ID | Severity | Area | Defect | Evidence | Status |
| --- | --- | --- | --- | --- | --- |
| BUG-QF-001 | High | Overview | "Latest run" card showed the oldest run on the page, not the newest | `qf-001-run-15b8e097…` (10-31 03:46) displayed while `/runs` listed `qf-000-run-f59d1f0d…` (11-01 15:42) first | **Fixed + verified** |
| BUG-QF-002 | Low | Overview | Missing p95 rendered as a bare unit: "p95: ms" | Rendered text `p95: ms` for a CANCELLED run with empty percentiles | **Fixed + verified** |
| BUG-QF-003 | Medium | Overview | "Baseline · QF-001" card could never populate for auto-named runs | "No QF-001 baseline is recorded yet." shown while many QF-001 runs existed | **Fixed + verified** |
| BUG-QF-004 | Medium | Compare | Same run selectable on both sides; failure surfaced as a bare "Comparison failed." | Backend answers `400 {"detail":"run ids must differ"}`; the UI discarded the detail | **Fixed + verified** |
| BUG-QF-005 | Medium | Run detail | FAILED run shows no failure reason and an empty log pane | `qf-000-run-26770c43…` renders FAILED with zeros and no diagnostic; run document has no error field | Deferred (needs backend field) |
| BUG-QF-006 | Low | Run detail | "LIVE: SUCCEEDED" / "LIVE: FAILED" badge shown for finished runs | Badge rendered on both a completed and a failed run | Open |
| BUG-QF-007 | Medium | Catalog / execution | Experiments can be launched against an empty products table with no warning | Readiness reports `0 rows`; catalog offers Start with no guard | Open |

### Non-defect environment finding

| ID | Finding | Impact |
| --- | --- | --- |
| ENV-QF-001 | The running stack uses `QF_SPRING_PROFILE=default`, so authentication is disabled and every request runs as an unrestricted operator. The authenticated profile (Keycloak + Mailpit) is not running. | Local-production readiness cannot be claimed for authentication until the `authenticated` profile is exercised. Deployment-mode gap, not a UI defect. |

## Detail

### BUG-QF-001 — Overview shows the wrong "latest" run

**Severity:** High · **Page:** `/` · **Status:** Fixed + verified

**Reproduction:**

1. Open `/runs` and note the first (newest) row: `qf-000-run-f59d1f0d-1793540552440`, 2026-11-01 15:42.
2. Open `/`.
3. Read the "Latest run" card.

**Expected:** `qf-000-run-f59d1f0d-1793540552440`.
**Actual:** `qf-001-run-15b8e097-1793411206290` (2026-10-31 03:46) — the 20th and oldest item on the page.

**Root cause:** `OverviewComponent.latestRun` returned `runs[runs.length - 1]`, assuming ascending order,
while `GET /api/runs` returns `startedAt DESC`.
**Impact:** The dashboard headline reports a stale, cancelled run as current.
**Fix:** New `mostRecent()` selects by `startedAt` in `frontend/src/app/components/overview/overview.component.ts`.
**Regression test:** `overview.component.spec.ts` — "shows the newest run even though the API returns newest first".
**Verification:** Live — the card now reads `qf-000-run-f59d1f0d…`, QF-000, SUCCEEDED, p95 43 ms, RPS 10.01
(`docs/qa/artifacts/qf-001-003-overview-fixed.png`).

### BUG-QF-002 — Bare unit rendered for a missing metric

**Severity:** Low · **Page:** `/` · **Status:** Fixed + verified

**Reproduction:** Open `/` when the newest run has empty `iterationPercentiles`.
**Expected:** An explicit "no data" marker. **Actual:** `p95: ms`.
**Root cause:** `{{value | number}}` renders nothing for `undefined`, leaving the trailing ` ms` behind.
**Fix:** `metricText()` renders `—` for missing or non-finite values and appends a unit only with a value.
**Regression test:** "renders an em dash instead of a bare unit when p95 is missing".
**Verification:** Live — the fixed page shows `p95: 43 ms` and never a bare unit.

### BUG-QF-003 — QF-001 card always claimed no baseline exists

**Severity:** Medium · **Page:** `/` · **Status:** Fixed + verified

**Reproduction:** Open `/` on a stack whose QF-001 runs are auto-named (`qf-001-run-<hex>`).
**Expected:** A QF-001 run is displayed, or a truthful empty state.
**Actual:** "No QF-001 baseline is recorded yet." despite QF-001 runs existing.
**Root cause:** `baselineRun` required `run.id.includes('baseline')`; auto-generated ids never contain it.
**Fix:** Prefer a legacy `baseline`-tagged id, else the most recent QF-001 run. The heading changed from
"Baseline · QF-001" to "QF-001 · latest" and the link from "Inspect baseline" to "Inspect run" so an arbitrary
run is not mislabelled — a deliberate copy change, recorded here.
**Regression test:** "shows the most recent QF-001 run instead of claiming none is recorded".
**Verification:** Live — card shows `qf-001-run-0c6dca4a-1793448441604`, p95 97 ms, p50 33 ms.

### BUG-QF-004 — Same run can be compared against itself

**Severity:** Medium · **Page:** `/compare` · **Status:** Fixed + verified

**Reproduction:**

1. Open `/compare`.
2. Select `qf-000-run-f59d1f0d-1793540552440` as **Baseline**.
3. Select the same run as **Candidate**.
4. Click **Compare**.

**Expected:** A clear refusal that explains why.
**Actual (before fix):** the request was sent; the backend answered
`400 {"type":"…/validation-error","title":"Bad Request","status":400,"detail":"run ids must differ"}` and the
UI rendered only `Comparison failed.`

**Root cause (two parts):** (a) no client-side guard, and (b) the HTTP interceptor unwraps RFC 7807 bodies to
the top level, so the backend detail lives on `err.detail`; the component read `err.error.detail` and fell
through to the generic string.
**Impact:** The comparison surface is the product's trust anchor; a self-comparison is a meaningless A/B
result, and a generic error hides an actionable cause.
**Fix:** `compare()` refuses identical ids without calling the API and explains why; the error handler reads
`detail`/`title` from both shapes (`frontend/src/app/components/compare/compare.component.ts`).
**Regression tests:** `compare.component.spec.ts` — "refuses to compare a run with itself and never calls the
API" and "surfaces the backend detail instead of a generic failure message".
**Verification:** Live — both selects held the same run id and the page rendered the refusal
(`docs/qa/artifacts/qf-004-compare-self-guard.png`).

### BUG-QF-005 — Failed runs carry no diagnostic (deferred)

**Severity:** Medium · **Page:** `/runs/:id` · **Status:** Deferred

**Reproduction:** Open `qf-000-run-26770c43-1793540422014` (state FAILED).
**Expected:** A failure reason or captured stderr tail.
**Actual:** State badge, zeroed throughput, "No hints", "No artifacts available.", empty log pane.
**Root cause:** `GET /api/runs/{id}` has no error/message field and streamed logs are not persisted, so the UI
has nothing to render. This is a backend data-model gap, not a template omission.
**Blocker:** Requires persisting a failure message/log tail on the run document — a backend change beyond a
defect fix. Recorded rather than hidden.

### BUG-QF-006 — "LIVE" badge on terminal runs

**Severity:** Low · **Page:** `/runs/:id` · **Status:** Open

A terminal run displays `LIVE: SUCCEEDED` / `LIVE: FAILED` next to an empty log panel. The badge only carries
meaning while a run is active. Not yet fixed.

### BUG-QF-007 — No guard against measuring an empty dataset

**Severity:** Medium · **Page:** `/experiments` · **Status:** Open

With `/api/lab/readiness` reporting `rowCount: 0`, the catalog still offers **Start experiment** with no
warning, so every measurement produced would be meaningless. Readiness is shown on `/` as "0 rows" but is not
surfaced or enforced at the launch point. Not yet fixed.

## Verification and limitations

| Check | Result |
| --- | --- |
| `frontend` Overview spec | 3/3 pass (isolated run) |
| `frontend` Compare spec | 2/2 pass (isolated run) |
| `frontend` full suite | **Not conclusive** — vitest worker processes aborted (`exit 134` / `0xC0000409`) mid-run, so the suite exited 1 without assertion failures. An earlier full run in this session passed 44/44 before the new specs existed. |
| Backend test suite | Not run this session |
| Live UI | Exercised at `/`, `/experiments`, `/runs`, `/runs/:id` (succeeded + failed), `/compare` |
| Logged out / authenticated flows | **Not exercised** — the running profile disables authentication |

## Operational notes

- The dev server on `:4200` exited once during the session while component files were being edited; no log for
  that instance survived, so the cause is unproven. It was restarted, and the restart initially bound a random
  port because this shell exports `PORT` ("Environment variable \"PORT\" detected. Using port 0."). Starting
  with `PORT=4200 npm start -- --host 127.0.0.1 --port 4200` pins it deterministically.
- No database, volume, benchmark artifact or user data was modified, reset or deleted during the session.
  Benchmark history and the `benchmark/results` directory were left intact.
