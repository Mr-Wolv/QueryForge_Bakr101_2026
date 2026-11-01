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
| Database | PostgreSQL 17.11 (compose, host port 56672) — `rowCount: 0` at session start, **1,000,000** after the C5 load |
| k6 | UP via `tools\k6\k6.exe` |
| Security profile | `QF_SPRING_PROFILE=default` — **authentication disabled**, `operator: true` |

## Summary

| Metric | Count |
| --- | --- |
| Total findings | 8 |
| Confirmed defects | 8 |
| Critical | 0 |
| High | 1 |
| Medium | 5 |
| Low | 2 |
| Fixed and verified in the live UI | 7 |
| Fixed, awaiting live verification (unit-tested; stack down after a machine crash) | 1 |
| Deferred | 0 |
| Open, not yet fixed | 0 |
| Not reproducible | 0 |
| Environment / deployment gaps (not app defects) | 1 |

## Defect index

| ID | Severity | Area | Defect | Evidence | Status |
| --- | --- | --- | --- | --- | --- |
| BUG-QF-001 | High | Overview | "Latest run" card showed the oldest run on the page, not the newest | `qf-001-run-15b8e097…` (10-31 03:46) displayed while `/runs` listed `qf-000-run-f59d1f0d…` (11-01 15:42) first | **Fixed + verified** |
| BUG-QF-002 | Low | Overview | Missing p95 rendered as a bare unit: "p95: ms" | Rendered text `p95: ms` for a CANCELLED run with empty percentiles | **Fixed + verified** |
| BUG-QF-003 | Medium | Overview | "Baseline · QF-001" card could never populate for auto-named runs | "No QF-001 baseline is recorded yet." shown while many QF-001 runs existed | **Fixed + verified** |
| BUG-QF-004 | Medium | Compare | Same run selectable on both sides; failure surfaced as a bare "Comparison failed." | Backend answers `400 {"detail":"run ids must differ"}`; the UI discarded the detail | **Fixed + verified** |
| BUG-QF-005 | Medium | Run detail | FAILED run shows no failure reason and an empty log pane | `qf-000-run-26770c43…` now renders "Why it failed: failed with exit code 107" plus the captured k6 stderr tail | **Fixed + verified** |
| BUG-QF-006 | Low | Run detail | "LIVE: SUCCEEDED" / "LIVE: FAILED" badge shown for finished runs | Badge rendered on both a completed and a failed run | **Fixed + verified** |
| BUG-QF-007 | Medium | Catalog / execution | Experiments can be launched against an empty products table with no warning | Readiness reports `0 rows`; catalog offers Start with no guard | **Fixed + verified** |
| BUG-QF-008 | Medium | Run detail / artifacts | Historical runs report "No artifacts available" even though their manifest lists stdout and stderr | `GET /api/runs/{id}/artifacts` returned only `manifest`; the `stderr` download worked, proving the file was reachable | **Fixed, awaiting live verification** |

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

### BUG-QF-005 — Failed runs carry no diagnostic

**Severity:** Medium · **Page:** `/runs/:id` · **Status:** Fixed + verified

**Reproduction:** Open `qf-000-run-26770c43-1793540422014` (state FAILED).
**Expected:** A failure reason or captured stderr tail.
**Actual (before):** State badge, zeroed throughput, "No hints", "No artifacts available.", empty log pane.

**Root cause:** the data already existed but was unreachable. `ExperimentOrchestrator` records an `outcome`
string on the run manifest and copies the runner stderr to a `stderr` artifact (kind `runner-stderr`), yet no
endpoint exposed either, so the detail page had nothing to render. The earlier note that this required a new
column was wrong: the manifest is the record of truth and `ddl-auto=none` stays untouched.

**Fix:** new read-only `GET /api/runs/{id}/diagnostics` returning `RunDiagnostics(runId, state, outcome,
stderrTail)`, with the tail capped at 4000 characters and empty when no artifact exists. The artifact is
matched by id `stderr` **or** `runner-stderr`, because manifests name it `stderr` while the kind is
`runner-stderr` — matching only the kind silently produced an empty tail on real runs. The run detail component
fetches diagnostics only for non-successful terminal states and renders a "Why it failed" block with the
outcome and the tail in a scrollable `<pre>`.

**Regression tests:** `ExperimentRunControllerUnitTest` — "reports outcome and stderr tail for a failed run",
"degrades to an empty tail when no stderr artifact exists", "answers 404 for an unknown run";
`run-detail.component.spec.ts` — "explains why a failed run failed instead of showing a bare state".

**Verification:** live `GET /api/runs/qf-000-run-26770c43-1793540422014/diagnostics` → `200`, `state: FAILED`,
`outcome: "failed with exit code 107"`, `stderrTail` 335 chars; the page renders both
(`docs/qa/artifacts/qf-005-failed-run-diagnostics-full.png`). The tail independently confirms that run's
original cause (`SyntaxError: … does not provide an export named "seededRandom"`), which commit `289204c` fixed.

### BUG-QF-006 — "LIVE" badge on terminal runs

**Severity:** Low · **Page:** `/runs/:id` · **Status:** Fixed + verified

A terminal run displays `LIVE: SUCCEEDED` / `LIVE: FAILED` next to an empty log panel. The badge only carries
meaning while a run is active.

**Fix:** the badge renders only while `isActive`; the log panel still renders when lines exist.
**Regression test:** `run-detail.component.spec.ts` — "does not advertise a live stream for a finished run".
**Verification:** live `/runs/qf-000-run-26770c43-1793540422014` shows state FAILED with no live badge
(`docs/qa/artifacts/qf-006-failed-run-no-live-badge.png`).

### BUG-QF-007 — No guard against measuring an empty dataset

**Severity:** Medium · **Page:** `/experiments` · **Status:** Fixed + verified

With `/api/lab/readiness` reporting `rowCount: 0`, the catalog still offers **Start experiment** with no
warning, so every measurement produced would be meaningless. Readiness is shown on `/` as "0 rows" but is not
surfaced or enforced at the launch point.

**Fix:** the catalog reads readiness, renders an explicit empty-dataset banner, disables **Start experiment**,
and refuses in `launch()` as well so a keyboard or programmatic path cannot bypass the disabled button.
**Regression tests:** `experiments.component.spec.ts` — "disables launching and explains why when the dataset
is empty" and "allows launching once the dataset is populated".
**Verification:** live catalog renders "The product dataset is empty (0 rows)" with Start disabled
(`docs/qa/artifacts/qf-007-empty-dataset-guard.png`).

### BUG-QF-008 — Historical runs report no artifacts even though their manifest lists them

**Severity:** Medium · **Page:** `/runs/:id` · **Status:** Fixed, awaiting live verification

**Reproduction:** Open `qf-000-run-26770c43-1793540422014`, whose manifest lists `stdout` and `stderr`.
**Expected:** the ARTIFACTS panel lists manifest, stdout, stderr.
**Actual:** "No artifacts available.", while `GET /api/runs/{id}/artifacts/stderr` downloaded the real stderr.

**Root cause:** two readers of the same data disagreed. Both `ExperimentRunService.artifacts()` and
`RunHistoryController`'s catalog route read `ExperimentRun.artifacts()` — the artifact list persisted on the
run *entity* — which is empty for runs recorded before artifact persistence existed. The manifest on disk is
the record of truth and already listed them, and `ArtifactService.resolveForRun` served them on direct
download, so the download worked while the listing did not.

**Fix:** `ExperimentRunService.artifacts()` falls back to the manifest entries when the persisted run lists
none, and the catalog route uses that service method instead of reading the entity directly. Recorded
artifacts still win; a run with no manifest artifacts still returns an empty list.

**Regression tests:** `ExperimentRunServiceArtifactsTest` — manifest fallback, recorded artifacts preferred,
empty when neither describes artifacts; `ExperimentRunControllerUnitTest` still 15/15.

**Verification:** unit-tested only — 18/18 green (`logs/qa-mvn-verify-artifacts.log`). The live re-check
needs the stack, and the machine crashed at that point, so the browser confirmation is genuinely outstanding
rather than claimed.

## Verification and limitations

| Check | Result |
| --- | --- |
| `frontend` Overview spec | 3/3 pass (isolated run) |
| `frontend` Compare spec | 2/2 pass (isolated run) |
| `frontend` full suite | **Fixed.** The abort was a native OOM (`FATAL ERROR: Zone Allocation failed`). With a bounded Vitest fork pool (`frontend/vitest.config.ts`, 2 forks, 4 GB heap) `npm test` reports **50 tests across 9 files, exit 0**, with no environment variables. |
| Backend test suite | **197 tests, 0 failures, 0 errors, 0 skipped** — `sh ./mvnw -B -ntp verify` → BUILD SUCCESS, 2026-11-01 (`logs/qa-mvn-verify.log`) |
| `ExperimentRunControllerUnitTest` (after QF-005) | **15/15 pass**, including the three diagnostics cases |
| `ExperimentRunServiceArtifactsTest` (BUG-QF-008) | **3/3 pass**; 18/18 with the controller suite |
| Live UI | Exercised at `/`, `/experiments`, `/runs`, `/runs/:id` (succeeded + failed), `/compare` |
| Logged out / authenticated flows | **Not exercised** — the running profile disables authentication |

## Operational notes

- The dev server on `:4200` exited once during the session while component files were being edited; no log for
  that instance survived, so the cause is unproven. It was restarted, and the restart initially bound a random
  port because this shell exports `PORT` ("Environment variable \"PORT\" detected. Using port 0."). Starting
  with `PORT=4200 npm start -- --host 127.0.0.1 --port 4200` pins it deterministically.
- No database, volume, benchmark artifact or user data was modified, reset or deleted during the session.
  Benchmark history and the `benchmark/results` directory were left intact.
- **2026-11-01 ~17:24 the machine crashed** (out-of-memory) during a Maven run that had only reached
  project scan. Docker Desktop, the host API on `:8080` and the Angular dev server on `:4200` all came down.
  Recovery was deliberately kept minimal: nothing was restarted, because every remaining step is a file
  write. Live verification of BUG-QF-008 is what was outstanding when it happened.
- The development bypass profile (`@Profile("!production & !local-auth")`) now disables CSRF. That profile
  authenticates nobody and issues no session cookie, so CSRF tokens protect nothing there; CSRF stays
  enforced by `ProductionSecurityConfig` for the `production` and `local-auth` profiles.
