# QueryForge — Convergence Plan

**Purpose:** record how close the application is to a defensible "done", and the ordered work that closes
the gap. Written 2026-11-01 at the end of the live bug-hunt session (see
[`BUG_HUNT.md`](./BUG_HUNT.md) for the defects themselves).

This is a **plan**, not evidence. Every percentage below is an engineering judgement whose weighting is
shown explicitly, so it can be argued with. Evidence references are dated; anything not re-verified this
session is labelled as prior evidence.

---

## 1. Definition of done

"100%" here means:

> A dependable, locally deployable QueryForge: authentication enforced, real data loaded, every user
> workflow verified in the running UI, no open medium-or-higher defects, and a test suite that reproduces
> its own result on demand.

Scoring against that definition:

- **~88%** overall (up from ~83% after C3–C5 landed on 2026-11-01).
- **~95%** against the narrower portfolio/demo scope used by
  [`../release-readiness.md`](../release-readiness.md), which explicitly excludes the production-shaped
  TLS deployment.

The gap is **verification coverage and environment state**, not architecture. Most remaining work is
proving and unblocking, not building.

---

## 2. Scorecard

| Area | Weight | Current state | Evidence basis | Score |
| --- | --- | --- | --- | --- |
| Core UI workflows (shell, overview, catalog, runs, run detail, compare) | 25% | All 7 live-session findings fixed, tested and verified in the browser — including a real FAILED run showing its outcome and stderr tail | Live UI session 2026-11-01 + `overview`/`compare`/`experiments`/`run-detail` specs | 98% |
| Execution engine & orchestration | 20% | Bounded k6 runner, run manifests, artifacts, stderr tailing, duration-unit fix | 143 backend tests green 2026-10-31; QF-DEF-06/07/08 verified fixed | 90% |
| Authentication & authorization | 15% | Implemented and previously accepted end-to-end; **not running now** | 9/9 real-stack pass 2026-10-31; QF-DEF-01..04, 10, 12 fixed. Unverified today | 85% |
| Evidence integrity (comparisons, plans, artifacts) | 10% | Verdict engine with compatibility blockers/warnings, exports, run-scoped plan route; self-comparison now refused client-side | Backend comparison tests; BUG-QF-004 fixed 2026-11-01 | 90% |
| Test automation & CI reproducibility | 10% | **Fixed.** The abort was a native V8 OOM; a bounded Vitest fork pool restores reproducibility (`50 tests, 9 files, exit 0`, no env vars) | `frontend/vitest.config.ts`, `logs/qa-test-config.log` | 92% |
| Data/environment readiness | 10% | **1,000,000 rows loaded and served by the API.** The stack had been running against an empty H2 database while the load went to the Compose Postgres; it now runs on PostgreSQL 17.11 | `/api/lab/readiness` `rowCount: 1000000`, 2026-11-01 | 95% |
| Ops: monitoring, email, backup/restore, restart | 5% | `[FIRING:1]`/`[RESOLVED]` delivered to Mailpit; `pg_dump`→`pg_restore` counts matched; restart persistence verified | `../release-readiness.md`, 2026-10-31 | 90% |
| Production-shaped TLS deployment | 5% | Configured, never deployed against real infrastructure | `docker-compose.production.yml`; verdict excludes it | 30% |

Weighted total: **≈ 88%**.

### Snapshot of the running environment (2026-11-01)

| Item | Observed |
| --- | --- |
| Branch / commit | `main` @ `05d91bc`, plus uncommitted work (see C1) |
| Compose services up | `db`, `backend`, `frontend`, `postgres-exporter` |
| Compose services absent | `keycloak`, `mailpit`, `prometheus`, `alertmanager` |
| Security profile | `QF_SPRING_PROFILE=default` → `authEnabled:false`, `operator:true`, development banner shown |
| Database | `rowCount: 1000000` (PostgreSQL 17.11) |
| k6 | UP (`tools\k6\k6.exe`) |
| Frontend build | Green — 358.30 kB initial / 97.81 kB transfer (budget warns at 400 kB) |
| Frontend specs | 9 files, **50 tests passing** (`npm test`, exit 0, no env vars) |

---

## 3. Convergence workstreams

Ordered by dependency. Effort is a rough range for one focused engineer.

### C1 — Land the working tree (0.5 h) — **DONE 2026-11-01**

**Why:** `LocalSecurityConfig.java` and `ProblemDetailConfig.java` are modified and uncommitted from an
earlier session, `Phase_6.md` is staged-then-deleted, and `AUDIT_FINDINGS.md` is both staged and modified.
A release cannot be called final on top of an unreviewed security-config change.

**Actions**

1. Read the diffs for `src/main/java/com/bakr/queryforge/config/LocalSecurityConfig.java` and
   `ProblemDetailConfig.java`; confirm intent (they appear to relate to local permit-all scope and
   RFC 7807 error mapping).
2. Decide on `Phase_6.md` (restore or leave deleted) and reconcile `AUDIT_FINDINGS.md`.
3. Commit this session's work as separate, reviewable commits: Overview fixes, Compare fix, the two spec
   files, `docs/qa/`.

**Acceptance:** `git status` clean; each commit builds and its tests pass.

### C2 — Repair frontend test reproducibility (1–2 h) — **DONE 2026-11-01**

**Why:** `npm test` aborts with vitest worker crashes (`exit 134`, `0xC0000409`) instead of reporting
assertions. Individual spec files pass. Until this is fixed, "the tests are green" is not a claim this
repository can make locally, which undermines every other row in the scorecard.

**Actions**

1. Reproduce on a quiet machine; capture the worker stderr rather than the filtered summary.
2. Try isolated-fork execution (`pool: 'forks'` with a single fork / `fileParallelism: false`) to confirm
   whether this is a pool/concurrency problem or a specific spec.
3. Suspects to eliminate in order: jsdom + missing `canvas` native module, memory pressure with
   `ng serve` running concurrently, and re-optimization races in the Angular unit-test builder.
4. Pin the working configuration in the project so CI and local runs agree.

**Acceptance:** `cd frontend && npm test` exits 0 with a full file count (currently 8 files) and no worker
crashes, on two consecutive runs.

### C3 — Close the open UI defects (0.5 d) — **DONE 2026-11-01**

| Defect | Action | Acceptance |
| --- | --- | --- |
| BUG-QF-007 (Medium) | Surface readiness at the launch point: disable **Start experiment** with an explanatory message when `rowCount` is 0, and warn when it is implausibly small for the selected `DATASET` | Component test asserting the button is disabled and the reason is rendered; live check with the empty database |
| BUG-QF-006 (Low) | Only render the `LIVE:` badge for non-terminal states | Component test for a terminal run |
| BUG-QF-005 (Medium, deferred) | Persist a failure reason: capture the stderr tail / last orchestrator error onto the run document, expose it via `GET /api/runs/{id}`, render it on run detail | Backend test asserting the field is populated for a deliberately failing run; frontend test asserting it renders |

**Outcome:** all three are fixed, tested and verified in the browser. BUG-QF-005 was solved without a schema
change: the outcome and the captured stderr were already on the manifest and as an artifact, so an additive
read-only `GET /api/runs/{id}/diagnostics` exposes them and the detail page renders a "Why it failed" block.
The earlier claim that this needed a new column was incorrect — `ddl-auto=none` still holds.

### C4 — Re-verify the authenticated stack end-to-end (0.5 d)

**Why:** The strongest evidence in the repo (9/9 real-stack, RBAC, CSRF, email) is dated 2026-10-31 and the
stack that produced it is not running. Current state cannot inherit that result.

**Actions**

1. `QF_SPRING_PROFILE=local-auth docker compose --profile app --profile authenticated --profile monitoring up -d --wait`
2. `QF_REAL_STACK=1 PLAYWRIGHT_BASE_URL=http://localhost:8080 npx playwright test e2e/real-stack.spec.ts`
3. Manually confirm: anonymous `/api/runs` → 401; `viewer` sees no Start button and receives 403 on write;
   operator can create/start/cancel; CSRF rejection message distinct from role rejection; sign-out leaves
   the app signed out on refresh.
4. Open Mailpit and confirm the alert pair after a deliberate backend stop.

**Acceptance:** 9/9 real-stack green against the running stack, plus the four manual checks recorded with
dated evidence in `docs/qa/artifacts/`.

### C5 — Load real data and take one honest measurement (1–2 h)

**Why:** `rowCount: 0` means no benchmark on this stack is evidence of anything.

**Actions**

1. Load a dataset (`docker compose run --rm data-generator 1000000` then `scripts/load-data.sql`, or 100k
   for speed), preserving the existing index-state workflow.
2. Run one QF-001 baseline and one indexed run; confirm Overview and Compare show real numbers.
3. Consider an explicit seed-on-first-boot path so a fresh clone does not silently present an empty dataset.

**Acceptance:** `/api/lab/readiness` reports the loaded row count; one A/B pair appears with real deltas and
a compatibility verdict.

### C6 — Sweep the remaining UI surfaces (0.5 d)

Not exercised in the 2026-11-01 session, though partly covered by prior automated suites:

- Plan explorer: tree/raw toggle, expand/collapse, copy, malformed plan, large plan, deep nesting, missing plan.
- Artifact download, ZIP archive, run export (Markdown/HTML), comparison export.
- Live SSE with a genuinely running run: state transitions, log lines, artifact notification, cancel,
  reconnect after a dropped stream.
- Keyboard-only traversal, focus visibility after navigation, accessible names on controls.
- Responsive layout at 320 / 768 / 1440 px.

**Acceptance:** each item exercised live and either passing or logged in `BUG_HUNT.md` with an ID.

### C7 — Re-run the backend suite (10 min, after C1) — **DONE 2026-11-01**

**Acceptance:** `runmvn.bat -B -ntp clean verify` (or `./mvnw -B -ntp clean verify`) reports 143+ tests,
0 failures, 0 skipped, with the `postgres:17` Testcontainers test actually running.

**Outcome:** `sh ./mvnw -B -ntp verify` → BUILD SUCCESS, **197 tests, 0 failures, 0 errors, 0 skipped**
(`logs/qa-mvn-verify.log`), including the real `postgres:17` Testcontainers coverage.

### C8 — Decide the production-shaped deployment (1–2 d, or scope it out)

Either deploy `docker-compose.production.yml` against real TLS/OIDC/SMTP infrastructure and verify it, or
explicitly strike it from the definition of done in `release-readiness.md`. Leaving it "configured but never
run" is the single largest remaining source of uncertainty in the scorecard.

---

## 4. Critical path

```
C1 (commit) ──> C2 (test runner) ──> C3 (3 defects) ──> C6 (UI sweep)
                     │
                     └──> C4 (auth re-verify) ──> C5 (data + measurement)
                                                     │
C7 (backend verify, after C1) ───────────────────────┴──> C8 (TLS deploy or scope out)
```

Realistic effort: **1.5–2 focused days** to a defensible 100% on the local-deployment definition; about a
week if the production TLS stack is included.

---

## 5. Exit criteria (machine-checkable)

```bash
# 1. Repository is clean
git status --porcelain          # expect empty

# 2. Backend suite
./mvnw -B -ntp clean verify     # expect 0 failures, 0 skipped

# 3. Frontend suite and build
cd frontend && npm test         # expect exit 0, no worker crashes
npm run build                   # expect success, initial bundle < 400 kB

# 4. Authenticated stack, real browser acceptance
QF_SPRING_PROFILE=local-auth docker compose --profile app --profile authenticated --profile monitoring up -d --wait
QF_REAL_STACK=1 PLAYWRIGHT_BASE_URL=http://localhost:8080 npx playwright test e2e/real-stack.spec.ts   # expect 9/9

# 5. Data is real, auth is enforced
curl -s localhost:8081/api/lab/readiness   # expect rowCount >= 100000
curl -s localhost:8081/api/auth/session     # expect authEnabled: true
curl -s -o /dev/null -w '%{http_code}\n' localhost:8081/api/runs   # expect 401 anonymously
```

---

## 6. Risks and non-goals

- **Risk:** the dirty security-config diff (C1) may change authorization behaviour in ways the current
  green suite does not cover. Review it before anything else.
- **Risk:** the frontend test-runner crash (C2) may be environmental rather than a code defect; if so, pin
  the working invocation and document it rather than chasing a phantom bug.
- **Risk:** loading 1M rows changes the numbers the README quotes. Those figures come from earlier benchmark
  datasets; re-measure before publishing new claims.
- **Non-goal:** redesign, new features, dependency upgrades, or architectural change. This plan is
  stabilization and proof only.
- **Non-goal:** re-opening defects already verified fixed (QF-DEF-01..13) without new evidence.

---

## 7. Change log

| Date | Change |
| --- | --- |
| 2026-11-01 | Initial plan. 7 findings from the live session, 4 fixed and verified, 3 open. Overall ≈ 80%. |
| 2026-11-01 | C1, C2 and most of C3 landed: 9 commits, 6 of 7 findings fixed and verified, frontend suite reproducible at 49/49. Overall ≈ 83%. |
| 2026-11-01 | C7 closed: backend suite green at 197 tests. Working tree clean; remaining work is C4 (authenticated stack), C5 (data), C6 (UI sweep) and the deferred QF-005. |
