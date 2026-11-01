# QueryForge — Smoke Test Results (2026-11-01)

**Scope:** Live Docker stack smoke test + existing Playwright suite  
**Stack:** `queryforge_bakr101_2026` Compose project, `default` profile (auth deferred)  
**Tester:** Automated smoke pass via browser + terminal  
**Date:** 2026-11-01

---

## Stack State at Test Time

| Service | Status | Ports |
|---------|--------|-------|
| `queryforge_bakr101_2026-db-1` | healthy | `127.0.0.1:54001->5432/tcp` |
| `queryforge_bakr101_2026-backend-1` | healthy | `127.0.0.1:8081->8080/tcp` |
| `queryforge_bakr101_2026-frontend-1` | healthy | `127.0.0.1:8080->80/tcp` |
| `queryforge_bakr101_2026-keycloak-1` | running | `8080/tcp, 8443/tcp, 9000/tcp` |
| `queryforge_bakr101_2026-postgres-exporter-1` | running | `9187/tcp` |

Database row count: **1,000,000**  
Backend health: **UP**  
Frontend health: **HTTP 200**

---

## Workflow Coverage

| Workflow | Live browser test | Automated test | Result |
| -------- | ----------------- | -------------- | ------ |
| Overview loads | ✅ Passed | — | Environment cards render; latest run and baseline shown |
| Catalog lists templates | ✅ Passed | — | QF-000 through QF-004 all visible with params |
| Experiment launch (QF-000) | ✅ Passed | — | Run created, k6 executed, state reached SUCCEEDED |
| Run detail / live logs | ✅ Passed | — | SSE logs stream; completion state shown |
| Run history table | ✅ Passed | — | Paginated list renders; new run appears at top |
| Run artifact download | ✅ Passed | — | `k6-summary` and `raw-k6` return 200 for new run |
| Compare page loads | ✅ Passed | — | Baseline/candidate dropdowns populated |
| Plan explorer (legacy run) | ❌ Failed | — | `explain-plan` artifact 404 for legacy imported run |
| Existing Playwright smoke (`e2e:ci`) | — | ✅ 3/3 passed | First-session routes green |
| Existing Playwright real-stack (`e2e:real`) | — | ⏭️ 10/10 skipped | Requires Keycloak/auth profile; deferred |

---

## Defect Ledger

### BUG-QF-003 — Missing host `benchmark/runs` directory breaks manifest writes

**Category:** Deployment  
**Severity:** High  
**Page:** Catalog → Start experiment  
**Workflow:** User launches any experiment from the catalog.

**Reproduction:**
1. Ensure host directory `benchmark/runs/` does not exist.
2. Start backend via `docker compose --profile app up -d backend`.
3. Open Catalog and click **Start experiment**.
4. Run immediately fails with state `FAILED` and toast: `failed to write manifest for ...: /app/benchmark/runs/...`

**Expected:** Experiment starts, manifest is written, run proceeds to completion.  
**Actual:** Run fails within ~122 ms with a filesystem error.  
**Root cause:** `docker-compose.yml` bind-mounts `./benchmark/runs:/app/benchmark/runs`, but the host directory was absent. Docker created a broken mount entry, leaving `/app/benchmark/runs` unusable in the container.  
**Fix:** Created `benchmark/runs/` on the host and restarted the backend container.  
**Status:** Fixed and verified — QF-000 completed successfully after fix.

---

### BUG-QF-004 — Legacy explain-plan artifact resolution returns 404

**Category:** Data-integrity  
**Severity:** Medium  
**Page:** Run detail → Explain plan  
**Workflow:** User opens a legacy imported run that has an `explain-plan` artifact listed in its metadata.

**Reproduction:**
1. Open run `qf001-baseline-search-1M-r10` (legacy imported run).
2. Click **View plan**.
3. UI shows toast: `The explain plan "explain-plan" could not be loaded.`
4. API call to `/api/runs/qf001-baseline-search-1M-r10/artifacts/explain-plan` returns 404.

**Expected:** The explain plan file `docs/experiments/plans/qf001-baseline-explain.txt` is served.  
**Actual:** 404 because `ArtifactService.resolveForRun` falls back to `resolve("explain-plan")`, which looks for `explain-plan.txt` or `explain-plan-explain.txt` in the plans directory. The actual file uses the run/template prefix naming convention (`{prefix}-explain.txt`), not the artifactId.  
**Root cause:** Legacy imported runs have no manifest file, so resolution falls back to artifactId-based lookup. The fallback logic does not map `explain-plan` artifactId to the legacy `{prefix}-explain.txt` file naming convention.  
**Status:** Confirmed, not fixed in this pass.

---

## Product-Sense Observations

- **Explain-plan gap is user-visible:** The run detail page lists `explain-plan` as an available artifact, but legacy runs cannot actually display it. This is misleading because the UI implies the artifact exists and is viewable.
- **Runs page transient empty-state text:** During earlier inspection, the runs page briefly displayed `No runs recorded yet.` at the top of the text extraction, even though the run table was present below it. This appears to be a transient render ordering issue rather than a persistent empty-state bug; the final rendered page shows the table correctly.
- **New-run workflow is coherent:** Launching QF-000 from the catalog, watching it execute, and viewing the completed run with artifacts is a smooth end-to-end flow.

---

## Security and Performance Notes

- No injection, path-traversal, or XSS defects were observed during the smoke test.
- Backend connects to PostgreSQL over the Compose internal network; no ports are exposed externally except loopback-bound host mappings.
- k6 execution is bounded by the template defaults (10 VUs, 30 s for QF-000); no unbounded resource consumption was observed.
- CORS, CSRF, and security header behavior was not the focus of this smoke pass; prior Playwright `e2e:real` coverage addressed those for the authenticated stack.

---

## Deferred / Out of Scope

- Authentication/authorization flows (Keycloak, OIDC, CSRF token enforcement in UI) — auth is deferred for local-dev.
- `e2e:real` Playwright suite — 10 tests skipped because they require the `local-auth` profile with Keycloak running.
- First-run empty-state UX on a truly fresh database — existing stack already has 1M rows and benchmark history.
- Full empty-state workflow with isolated Compose project and fresh volumes.

---

## Final Verdict

**READY WITH DOCUMENTED LIMITATIONS**

The core local-dev stack is functional:
- Backend and frontend start through Docker Compose.
- Users can browse the catalog, launch experiments, watch them execute, and inspect completed runs.
- Existing Playwright smoke suite passes.

Known limitations are tracked as BUG-QF-003 (fixed) and BUG-QF-004 (open, medium severity, legacy data path). No release blockers remain in the primary user workflow.
