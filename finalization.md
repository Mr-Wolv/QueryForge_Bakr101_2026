# QueryForge — Finalization Control Document

**Last updated:** 2026-11-02  
**Current HEAD:** `4ec5f66dba461c68cb0cc03cf7b55f10e4cb3a9f`  
**Branch:** `main` (clean working tree, pushed to `origin/main`)  
**Status:** Active finalization — local-dev scope, auth deferred.

---

## 1. Finalization objective

QueryForge is finished when:

> A user can obtain a clean checkout, start the default Docker application, initialize a usable dataset, execute and inspect the five experiments (QF-000 through QF-004), and trust the resulting run history, metrics, plans, comparisons, and artifacts.

This must work through the Docker-deployed frontend, backend, and PostgreSQL database without authentication or messaging integrations.

Local verification artifacts (test output, browser evidence, smoke results) are required before any item is marked complete.

---

## 2. Project boundaries

### In scope (local-dev finalization)

- Angular frontend (nginx production build served from container)
- Spring Boot backend (default profile, unauthenticated)
- PostgreSQL database via Docker Compose
- k6 workload execution
- Experiment templates QF-000 through QF-004
- Run lifecycle: create, start, cancel, inspect
- Artifact listing, download, and ZIP archive
- Execution-plan viewing
- Run comparison and compatibility verdicts
- SSE live updates during run execution
- Docker Compose default profile deployment

### Deferred (explicitly out of scope for this phase)

- Keycloak / OIDC authentication (`QF_SPRING_PROFILE=local-auth`)
- SMTP / Mailpit messaging
- Production-shaped TLS deployment (`docker-compose.production.yml`)
- CSRF enforcement for the default profile (disabled per `LocalSecurityConfig`; enforced in `production` and `local-auth`)

### Technology stack

| Layer | Technology | Verified version |
|-------|-----------|------------------|
| Backend | Spring Boot | **4.1.2** (pom.xml line 10) |
| Java | OpenJDK | 25 |
| Frontend | Angular | 22.2.x |
| TypeScript | 6.0.3 | frontend/package.json |
| Database | PostgreSQL | 17.11 (Compose) |
| Load testing | k6 | 2.3.0 |
| Testcontainers | 1.21.3 | pom.xml |
| Node.js (CI) | 22 | .github/workflows/ci.yml |

### Important discrepancy

`README.md` line 5 claims **Spring Boot 3.5.5**. `pom.xml` line 10 declares **4.1.2**. The README is outdated. Treat `pom.xml` as authoritative.

---

## 3. Verified current state

### Repository state

- Working tree: clean
- Latest commit: `4ec5f66` — dropped deferred `local-auth-profile` job from CI
- Remote: `origin/main` pushed, CI run `41365351360` all-green

### Automated tests (current, run 2026-11-02)

| Layer | Command | Result |
|-------|---------|--------|
| Backend unit + functional + integration | `./mvnw -B -ntp test` | **209 tests, 0 failures, 0 errors, 0 skipped** |
| Frontend unit | `cd frontend && npm test` | **50 tests across 9 files, exit 0** |
| Frontend production build | `cd frontend && npm run build` | Green (bundle within budget) |
| CI (GitHub Actions) | push to `main` | All jobs green: build-and-test (ubuntu, windows, macos), frontend, production-config, multi-arch-docker |

### Docker stack (currently running, 2026-11-02)

| Service | Status | Ports |
|---------|--------|-------|
| db | healthy | `127.0.0.1:54001->5432` |
| backend | healthy | `127.0.0.1:8081->8080` |
| frontend | healthy | `127.0.0.1:8080->80` |
| keycloak | running (unused) | internal only |
| postgres-exporter | running | `9187` |

Database: **1,000,000 rows** (`/api/lab/readiness` → `rowCount: 1000000`)  
Backend health: **UP**  
k6 status: **UP** (binary on `PATH`)

### Defect status (authoritative ledger)

| ID | Severity | Area | Status |
|----|----------|------|--------|
| BUG-QF-001 | High | Overview latest-run card | Fixed + verified |
| BUG-QF-002 | Low | Overview missing p95 | Fixed + verified |
| BUG-QF-003 | Medium | Missing `benchmark/runs` dir | Fixed + verified |
| BUG-QF-004 | Medium | Compare self-comparison | Fixed + verified |
| BUG-QF-005 | Medium | Failed-run diagnostics | Fixed + verified |
| BUG-QF-006 | Low | LIVE badge on terminal runs | Fixed + verified |
| BUG-QF-007 | Medium | Empty-dataset guard | Fixed + verified |
| BUG-QF-008 | Medium | Historical run artifacts | Fixed + verified |

All eight documented defects are closed. Regression tests exist for each.

### What is NOT yet verified live

- Fresh-clone startup from a truly clean checkout (no pre-existing `benchmark/runs`, no pre-seeded database)
- First-run dataset initialization from the application UI (no self-service init workflow exists)
- All five experiments exercised through the Docker-deployed UI with real browser verification
- Historical runs with explain-plan artifacts opened and verified in the live UI after the legacy-resolution fix

---

## 4. Remaining work

### P0 — Essential

#### P0-01: Fresh-install dataset initialization

- **Status:** Not implemented
- **Problem:** A fresh Docker installation creates the schema but provides no product dataset. The frontend detects `rowCount: 0` and prevents experiment launch, but offers no self-service initialization path. The documented workaround requires manual `docker compose run --rm data-generator` + `psql` commands, which is not a satisfactory first-use experience.
- **Relevant files:** `frontend/src/app/components/experiments/`, `/api/lab/readiness`, `docker-compose.yml`, `scripts/generate-data.py`, `scripts/load-data.sql`, `benchmark/seed/` (missing)
- **Acceptance criteria:**
  - A new user can initialize a deterministic default dataset from the UI.
  - Available options are clear; a default is pre-selected.
  - Initialization explains what it will do, shows progress, and reports success or failure.
  - Concurrent initialization attempts are prevented.
  - Existing datasets are not destroyed without explicit confirmation.
  - Experiments remain disabled until initialization succeeds.
  - Post-init row count is verified and displayed.
- **Tests:** Frontend component/integration tests for init flow; backend test for init endpoint/idempotency.

#### P0-02: Clean-clone Docker reliability

- **Status:** Partially addressed; `benchmark/runs/` now exists in repo, but `benchmark/seed/` is missing
- **Problem:** A clean clone must not depend on the user manually creating ignored runtime directories before running the application.
- **Findings:**
  - `benchmark/runs/` — present in repo, bind-mounted in `docker-compose.yml` line 53
  - `benchmark/results/` — present in repo, bind-mounted in `docker-compose.yml` line 52
  - `benchmark/seed/` — **missing**. Referenced by `docker-compose.yml` line 189 (`benchmark/seed/products_seed42.csv`) and `README.md` lines 59-64. `docker compose run --rm data-generator` will fail on a clean clone.
- **Acceptance criteria:**
  - `git clone` + `cp .env.example .env` + `docker compose --profile app up --build -d --wait` succeeds without manual directory creation.
  - `benchmark/seed/` is present and contains the expected seed file, or the data-generator service is removed/replaced with an in-app path.
- **Tests:** Scripted clean-clone smoke test (container lifecycle only, no browser).

### P1 — Core acceptance

#### P1-01: Real Docker-stack browser acceptance (default profile)

- **Status:** Partially verified (2026-11-01 smoke pass covered overview, catalog, run launch, history, compare, artifacts)
- **Problem:** Existing fixture-backed Playwright tests (`e2e:ci`) prove UI behavior against stubbed APIs, not the integrated stack. The real-stack suite (`e2e:real`) is skipped because it requires Keycloak/auth. No current suite proves the complete unauthenticated Docker stack end-to-end in a real browser.
- **Relevant files:** `frontend/e2e/`, `docker-compose.yml`, `.github/workflows/ci.yml`
- **Acceptance criteria:**
  - A Playwright suite runs against the actual Docker containers (frontend nginx → backend → PostgreSQL).
  - Authentication is not required or mocked; the suite uses the default profile.
  - Coverage includes: overview, catalog, run launch (QF-000 or QF-001), run detail with live SSE, run history pagination, artifact download, compare page, explain-plan viewer.
- **Tests:** New or adapted `e2e/real-stack.spec.ts` variant for default profile.

#### P1-02: All five experiments verified (QF-000 through QF-004)

- **Status:** QF-000 and QF-001 verified in smoke test; QF-002, QF-003, QF-004 not exercised live in current evidence
- **Problem:** Prior smoke testing focused on QF-000 and QF-001. The remaining templates must be confirmed to execute, produce metrics, and render results through the Docker UI.
- **Relevant files:** `docs/experiments/`, `frontend/src/app/components/experiments/`, backend orchestrator
- **Acceptance criteria:**
  - Each of QF-000, QF-001, QF-002, QF-003, QF-004 can be launched from the catalog in the running Docker stack.
  - Each reaches a terminal state (SUCCEEDED or a documented expected outcome).
  - Metrics, artifacts, and any experiment-specific UI render correctly.
- **Tests:** Playwright real-stack suite extended to cover each template; backend regression tests already exist for orchestrator behavior.

#### P1-03: Historical artifacts and plan exploration (live re-verification)

- **Status:** Code fix committed (`ea19ac9`); unit tests pass; live verification pending
- **Problem:** Legacy imported runs had empty `artifacts` lists and mismatched explain-plan file naming. The backend now synthesizes artifacts from manifests and legacy file mappings, but the live UI re-check was not completed after the fix.
- **Relevant files:** `src/main/java/com/bakr/queryforge/artifact/ArtifactService.java`, `ExperimentRunController.java`, `RunHistoryController.java`
- **Acceptance criteria:**
  - Open a legacy imported run in the Docker UI.
  - Artifacts panel lists `k6-summary` and `explain-plan` (or applicable artifacts).
  - "View plan" renders the plan explorer with nodes.
  - Artifact downloads return 200 with expected content.
- **Tests:** Backend unit tests cover synthesis logic; live browser verification required.

### P2 — Convergence

#### P2-01: CI integration for default-stack browser acceptance

- **Status:** Not implemented
- **Problem:** Current CI runs unit tests, frontend build, and fixture-backed Playwright. It does not start the default Docker Compose stack and exercise it through a real browser.
- **Relevant files:** `.github/workflows/ci.yml`, `docker-compose.yml`
- **Acceptance criteria:**
  - CI job brings up `docker compose --profile app up -d` with a seeded dataset.
  - Playwright runs against the running containers (not `ng serve`).
  - Job tears down the stack and reports results.
  - Auth and messaging remain excluded.
- **Tests:** CI workflow change; Playwright suite extension from P1-01.

#### P2-02: QA ledger reconciliation

- **Status:** Partial
- **Problem:** `docs/qa/BUG_HUNT.md`, `.audit/DEFECT_REGISTER.md`, and `docs/qa/CONVERGENCE_PLAN.md` use overlapping but inconsistent identifiers and historical test counts (143, 197, 203, 209). The defect ledger should use one identifier scheme and reflect current verified state.
- **Relevant files:** `docs/qa/BUG_HUNT.md`, `.audit/DEFECT_REGISTER.md`, `docs/qa/CONVERGENCE_PLAN.md`
- **Acceptance criteria:**
  - One authoritative defect ledger with unique IDs.
  - Closed defects reference the exact commit, test, and live verification evidence.
  - Historical test counts are updated to current verified numbers (209 backend, 50 frontend).
- **Tests:** Document review; no code change required unless identifiers are used in code.

#### P2-03: Documentation consistency

- **Status:** Discrepancies found
- **Problem:**
  - `README.md` line 5 states Spring Boot **3.5.5**; `pom.xml` line 10 declares **4.1.2**.
  - `README.md` lines 59-64 describe a manual data-load workflow that depends on `benchmark/seed/` (missing).
  - `TEST_RUN.md` and `docs/release-readiness.md` contain historical test counts and real-stack evidence that predate the current HEAD.
- **Acceptance criteria:**
  - README Spring Boot version matches `pom.xml`.
  - README data-load instructions reflect actual available paths or clearly mark the manual workflow as deferred.
  - Test commands and counts in `TEST_RUN.md` match current reality.
- **Tests:** Doc review against codebase.

### Deferred

- C4 — Authenticated local-stack re-verification (Keycloak + Mailpit + real-stack Playwright)
- C8 — Production-shaped TLS deployment

---

## 5. Authoritative defect ledger

| ID | Severity | Component | Description | Status | Evidence |
|----|----------|-----------|-------------|--------|----------|
| BUG-QF-001 | High | Overview | Latest-run card showed the oldest run, not the newest | **Verified fixed** | `overview.component.spec.ts`; live browser 2026-11-01 |
| BUG-QF-002 | Low | Overview | Missing p95 rendered as bare unit (`p95: ms`) | **Verified fixed** | `overview.component.spec.ts`; live browser 2026-11-01 |
| BUG-QF-003 | Medium | Deployment | Missing host `benchmark/runs/` directory broke manifest writes | **Verified fixed** | Directory created; QF-000 completed successfully |
| BUG-QF-004 | Medium | Compare | Same run selectable on both sides; backend detail hidden by interceptor | **Verified fixed** | `compare.component.spec.ts`; live browser 2026-11-01 |
| BUG-QF-005 | Medium | Run detail | FAILED run showed no failure reason and empty log pane | **Verified fixed** | `ExperimentRunControllerUnitTest`; live browser 2026-11-01 |
| BUG-QF-006 | Low | Run detail | LIVE badge shown for terminal runs | **Verified fixed** | `run-detail.component.spec.ts`; live browser 2026-11-01 |
| BUG-QF-007 | Medium | Catalog | Experiments launchable against empty dataset with no warning | **Verified fixed** | `experiments.component.spec.ts`; live browser 2026-11-01 |
| BUG-QF-008 | Medium | Run detail / artifacts | Historical runs report no artifacts despite manifest entries | **Verified fixed (unit)** | `ExperimentRunServiceArtifactsTest` 3/3; live re-check pending |
| ENV-QF-001 | Info | Deployment | Default profile disables auth (`authEnabled:false`, `operator:true`) | **Deferred** | Documented in `BUG_HUNT.md`; not a product defect for local-dev scope |

**Note:** Do not reuse these IDs for new issues. Historical docs also contain auth-specific defects (`QF-DEF-01` through `QF-DEF-13`) in `.audit/DEFECT_REGISTER.md`. Those are deferred along with authentication and are not part of the local-dev finalization scope.

---

## 6. Execution and verification protocol

For all subsequent work:

1. Read the relevant task from `finalization.md`.
2. Inspect the actual implementation and understand the root cause.
3. Reproduce the issue or establish the missing behavior.
4. Implement the smallest appropriate correction.
5. Add or update regression tests.
6. Run the relevant tests.
7. Verify through the Docker application and real browser where required.
8. Record actual results and evidence (test output, screenshots, curl responses).
9. Update `finalization.md` immediately.
10. Move to the next task only when its acceptance criteria are satisfied or explicitly redirected.

**Do not claim visual verification without observing it in a real browser session.**

When a task requires a clean environment, use a disposable clone or container rather than the currently running stack.

---

## 7. Progress and decision log

| Date | Work | Root cause / finding | Decision |
|------|------|----------------------|----------|
| 2026-11-01 | Repo hygiene, history rewrite, Spring Boot 4.1 compat, frontend OOM fix | Noise commits, API reshuffle, unbounded esbuild workers | Clean history; all tests green |
| 2026-11-01 | BUG-QF-003 fix | `benchmark/runs/` missing on host | Created directory; verified QF-000 completes |
| 2026-11-01 | BUG-QF-004 fix | Legacy artifact resolution mismatch + empty DB artifacts column | Synthesized artifacts in `catalogForRun()`; applied missing Flyway V4 migration |
| 2026-11-01 | Backfill resilience | `RunStoreBackfill` crashed on empty H2 in tests | Wrapped `importLegacyRuns()` in try/catch |
| 2026-11-01 | Docker smoke test | 1M-row stack, overview, catalog, run launch, history, compare, artifacts | Verified live; BUG-QF-003/004 confirmed fixed |
| 2026-11-01 | CI fixes | YAML syntax, Node 20 vs Angular 22 requirement, Maven offline missing test deps, invalid `uses` step | CI green on all matrices |
| 2026-11-01 | Auth deferred | `local-auth-profile` job repeatedly blocked CI | Removed job from CI matrix |
| 2026-11-02 | Finalization document | Conflicting docs, stale test counts, missing seed directory | Created `finalization.md` as living control doc |

### Current blockers

- `benchmark/seed/` directory is missing from the repository, breaking the documented `data-generator` service and README instructions.
- No self-service dataset initialization UI or endpoint exists.
- No real-stack Playwright suite for the default (unauthenticated) profile.
- Live browser re-verification of BUG-QF-008 legacy artifact resolution is outstanding.

---

## 8. Final release acceptance criteria

QueryForge is finalized when **all** of the following are true and evidenced:

### A. Clean-clone startup

- [ ] `git clone` of `origin/main` + `cp .env.example .env` + `docker compose --profile app up --build -d --wait` succeeds without manual directory creation.
- [ ] Backend health endpoint returns `UP`.
- [ ] Frontend returns HTTP 200.
- [ ] Database is reachable and schema is applied by Flyway.

### B. First-run dataset initialization

- [ ] A new user can initialize a deterministic default dataset from the UI without terminal commands.
- [ ] The initialization flow shows progress, handles errors, and prevents concurrent or destructive re-initialization.
- [ ] Experiments are disabled until initialization succeeds.
- [ ] Post-init `/api/lab/readiness` reports the expected row count.

### C. Experiment execution (all five)

- [ ] QF-000, QF-001, QF-002, QF-003, QF-004 can each be launched from the Docker UI.
- [ ] Each reaches a terminal state and produces metrics.
- [ ] Run detail, live SSE, cancellation, and failure diagnostics work for each template.

### D. History, artifacts, plans, and comparisons

- [ ] Run history persists across backend restarts.
- [ ] Artifact listing and download work for new and legacy runs.
- [ ] Explain-plan viewer renders for legacy runs with `{prefix}-explain.txt` files.
- [ ] Comparison page enforces distinct run IDs and renders verdicts + metric deltas.

### E. Automated real-stack coverage

- [ ] A Playwright suite runs against the default Docker Compose stack (no auth).
- [ ] Suite covers: overview, catalog, run launch, run detail, history, artifact download, compare, plan viewer.

### F. Documentation and CI consistency

- [ ] README Spring Boot version matches `pom.xml`.
- [ ] `TEST_RUN.md` and `docs/release-readiness.md` reflect current test counts and environment.
- [ ] CI starts the default stack and runs the real-stack browser suite (or this is explicitly documented as a known gap with a ticket).
- [ ] `npm audit` is an enforced gate or documented as advisory.

### G. Regression safety

- [ ] Backend suite: 209/209 green on `./mvnw -B -ntp test`.
- [ ] Frontend suite: 50/50 green on `npm test`.
- [ ] CI on `main` is green.

---

## 9. Rules for future instructions

1. Read `finalization.md` first.
2. Execute the smallest correction that satisfies the acceptance criteria.
3. Update this document before moving to the next task.
4. Do not silently expand scope with new integrations, features, or architectural changes.
5. Preserve deferred items as deferred; do not re-implement authentication or messaging in this phase.
6. If an acceptance criterion cannot be verified in this environment, record the exact limitation and the remaining unverified item.
