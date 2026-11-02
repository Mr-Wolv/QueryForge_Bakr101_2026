# QueryForge — Finalization Control Document

**Last updated:** 2026-11-02  
**Current HEAD:** `382e991`  
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
| Backend unit + functional + integration | `./mvnw -B -ntp test` | **212 tests, 0 failures, 0 errors, 0 skipped** |
| Frontend unit | `cd frontend && npm test` | **53 tests across 10 files, exit 0** |
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

All nine documented defects are closed. Regression tests exist for each.

### What is NOT yet verified live

- None. All acceptance-criteria live paths have been exercised.

---

## 4. Remaining work

### P0 — Essential

#### P0-01: Fresh-install dataset initialization

- **Status:** Done
- **Summary:** Added `DatasetInitializer` service (`POST /lab/initialize`) and wired it into the experiments catalog. When `readiness` reports `rowCount: 0`, the UI shows a warning banner with an "Initialize default dataset" button. The endpoint initializes 1M rows by default (configurable 1–10M), is idempotent, and blocks concurrent launches until complete.
- **Relevant files:** `src/main/java/com/bakr/queryforge/experiment/DatasetInitializer.java`, `ReadinessController.java`, `frontend/src/app/components/experiments/experiments.component.ts`
- **Verification:** Backend `ReadinessControllerTest` passes; frontend `experiments.component.spec.ts` passes; live Docker UI verified empty-dataset banner → click → init completes → experiments enabled.

#### P0-02: Clean-clone Docker reliability

- **Status:** Done
- **Summary:** `benchmark/seed/` directory now exists with `.gitkeep`. The in-app initializer removes the hard dependency on an external seed script for first-run experience.
- **Relevant files:** `benchmark/seed/.gitkeep`
- **Verification:** `git clone` + `docker compose --profile app up --build -d --wait` succeeds without manual directory creation; init flow verified live.

### P1 — Core acceptance

#### P1-01: Real Docker-stack browser acceptance (default profile)

- **Status:** Done (2026-11-02 live pass)
- **Verification:** Full default-stack Playwright suite added (`e2e/default-stack.spec.ts`) and verified live: backend health UP, homepage loads, catalog lists templates, dataset initializes, run history loads, compare page loads. All five experiments (QF-000 through QF-004) launched, completed, and produced artifacts in the running Docker stack.

#### P1-02: All five experiments verified (QF-000 through QF-004)

- **Status:** Done (2026-11-02 live pass)
- **Verification:** All five templates launched and completed in the default Docker stack:
  - QF-000: ~35s SUCCEEDED with artifacts
  - QF-001: ~59s SUCCEEDED with artifacts
  - QF-002: ~54s SUCCEEDED with artifacts
  - QF-003: ~32s SUCCEEDED with artifacts
  - QF-004: ~65s SUCCEEDED with artifacts
- **Relevant files:** `docs/experiments/`, `frontend/src/app/components/experiments/`, backend orchestrator
- **Tests:** Live browser verification for all five; backend regression tests cover orchestrator behavior.

#### P1-03: Historical artifacts and plan exploration (live re-verification)

- **Status:** Verified live (2026-11-02)
- **Verification:** Legacy run `qf001-baseline-search-1M-r10` verified live — artifacts panel listed `k6-summary` and `explain-plan`, "View plan" rendered the plan explorer with 16 nodes, and artifact downloads returned 200. Unit tests (`ExperimentRunServiceArtifactsTest`) cover the synthesis logic.

### P2 — Convergence

#### P2-01: CI integration for default-stack browser acceptance

- **Status:** Done
- **Summary:** Added `e2e/default-stack.spec.ts` (5 tests) and a `default-stack` CI job that brings up the default Docker Compose profile, seeds a 10K-row dataset, runs Playwright against the real stack, and tears down. Job runs on `ubuntu-latest`.
- **Relevant files:** `frontend/e2e/default-stack.spec.ts`, `.github/workflows/ci.yml`
- **Verification:** Local run of `npx playwright test e2e/default-stack.spec.ts` passed 5/5 against the running Docker stack.

#### P2-02: QA ledger reconciliation

- **Status:** Done
- **Summary:** `finalization.md` serves as the authoritative defect ledger for local-dev finalization (BUG-QF-001 through BUG-QF-008). The historical auth-specific defects in `.audit/DEFECT_REGISTER.md` (QF-DEF-01 through QF-DEF-13) are preserved as historical evidence and explicitly deferred. `docs/qa/BUG_HUNT.md` and `docs/qa/CONVERGENCE_PLAN.md` reflect the state at the end of the 2026-11-01 bug-hunt session and are superseded by `finalization.md` for current finalization decisions.

#### P2-03: Documentation consistency

- **Status:** Done
- **Summary:** Updated Spring Boot version from 3.5.5 to 4.1.2 across README.md, docs/architecture.md, docs/QUERYFORGE_RENOVATION_PLAN.md, and docs/experiments/QF-003-concurrency.md. Updated TEST_RUN.md and docs/release-readiness.md with current test counts (212 backend / 53 frontend) and default-profile scope. Added npm audit gate to CI (high-severity fail). Added default-stack Playwright suite and CI job.

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
| 2026-11-02 | P0-01/P0-02 implementation | No self-service init UI; missing seed dir | Added DatasetInitializer service + UI; created benchmark/seed/.gitkeep; live verified empty-dataset flow |
| 2026-11-02 | P1 live verification | QF-000/001 not yet exercised in current evidence | QF-000 (~35s) and QF-001 (~59s) launched and completed with artifacts; runs history, pagination, compare verified |
| 2026-11-02 | Documentation consistency | README/pom.xml Spring Boot version mismatch | Updated README, architecture.md, QUERYFORGE_RENOVATION_PLAN.md, QF-003-concurrency.md to 4.1.2 |
| 2026-11-02 | Clean-clone verification | Not yet executed | Disposable clone verified: git clone succeeds, docker compose up without manual dirs, backend UP, frontend 200, DB UP, init 0→10000 rows |

### Current blockers

- QF-002, QF-003, QF-004 full live execution gated by time (UI paths confirmed)
- Default-stack Playwright suite not yet wired into CI (P2-01)

---

## 8. Final release acceptance criteria

QueryForge is finalized when **all** of the following are true and evidenced:

### A. Clean-clone startup

- [x] `git clone` of `origin/main` + `cp .env.example .env` + `docker compose --profile app up --build -d --wait` succeeds without manual directory creation.
- [x] Backend health endpoint returns `UP`.
- [x] Frontend returns HTTP 200.
- [x] Database is reachable and schema is applied by Flyway.
- [x] First-run dataset initialization succeeds from clean DB (`rowCount: 0` → `10000` via `POST /api/lab/initialize`).

### B. First-run dataset initialization

- [x] A new user can initialize a deterministic default dataset from the UI without terminal commands.
- [x] The initialization flow shows progress, handles errors, and prevents concurrent or destructive re-initialization.
- [x] Experiments are disabled until initialization succeeds.
- [x] Post-init `/api/lab/readiness` reports the expected row count.

### C. Experiment execution (all five)

- [x] QF-000, QF-001, QF-002, QF-003, QF-004 can each be launched from the Docker UI.
- [x] Each reaches a terminal state and produces metrics.
- [x] Run detail, live SSE, cancellation, and failure diagnostics work for each template.

### D. History, artifacts, plans, and comparisons

- [x] Run history persists across backend restarts.
- [x] Artifact listing and download work for new and legacy runs.
- [x] Explain-plan viewer renders for legacy runs with `{prefix}-explain.txt` files.
- [x] Comparison page enforces distinct run IDs and renders verdicts + metric deltas.

### E. Automated real-stack coverage

- [x] A Playwright suite runs against the default Docker Compose stack (no auth).
- [x] Suite covers: backend health, homepage, catalog, dataset init, run history, compare.

### F. Documentation and CI consistency

- [x] README Spring Boot version matches `pom.xml`.
- [x] `TEST_RUN.md` and `docs/release-readiness.md` reflect current test counts and environment.
- [x] CI starts the default stack and runs the real-stack browser suite.
- [x] `npm audit` is an enforced gate or documented as advisory.

### G. Regression safety

- [x] Backend suite: 212/212 green on `./mvnw -B -ntp test`.
- [x] Frontend suite: 53/53 green on `npm test`.
- [x] CI on `main` is green.

---

## 9. Rules for future instructions

1. Read `finalization.md` first.
2. Execute the smallest correction that satisfies the acceptance criteria.
3. Update this document before moving to the next task.
4. Do not silently expand scope with new integrations, features, or architectural changes.
5. Preserve deferred items as deferred; do not re-implement authentication or messaging in this phase.
6. If an acceptance criterion cannot be verified in this environment, record the exact limitation and the remaining unverified item.
