# QueryForge — To Be Announced (Maintenance / Next-Development Plan)

This file captures the agreed maintenance and gap-closure work for QueryForge.
It is intended as the **next-session entry point** when development resumes.

Scope: local-dev finalization and trustworthiness of completion claims.
Out of scope for this plan: authentication, Keycloak, SMTP, Mailpit, messaging, OIDC.

---

## Current Known HEAD

```
69b6e1b1acff67693712d94647cf39e2b2f65a10
```

## Guiding principle

Do not expand scope. Reconcile the existing finalization document with actual
implementation and CI evidence, then close the confirmed gaps.

---

## 1. Preserve completed work

Do not reopen these items without new evidence:

- Fresh Docker clone startup
- UI-triggered default dataset initialization
- QF-000 through QF-004 live execution
- Historical artifact and execution-plan verification
- Default Docker Compose browser suite
- CI integration
- Spring Boot documentation alignment
- Existing UI/deployment defects (BUG-QF-001 through BUG-QF-008)

---

## 2. Reconcile `finalization.md`

The document currently contains contradictory and outdated sections.

Required corrections:

- Update recorded HEAD to current `main`.
- Reconcile contradictory P1 completion claims and blockers.
- Reconcile BUG-QF-008 live-verification status across sections.
- Correct defect count and ledger descriptions.
- Update test/CI evidence tables.
- Remove historical blockers that are already closed.
- Clearly distinguish manual live verification from automated CI coverage.

Do not mark anything complete solely based on commit messages.

---

## 3. Confirmed gaps to close

### A. Dataset initialization correctness

**Files to inspect:**
- `src/main/java/com/bakr/queryforge/experiment/DatasetInitializer.java`
- `frontend/src/app/components/experiments/experiments.component.ts`

**Problem:** Concurrent initialization requests may race. Current implementation
is transactional but not visibly serialized at the database level.

**Required work:**
1. Reproduce concurrent initialization against PostgreSQL.
2. If unsafe, add the smallest appropriate database-level protection.
3. Add regression tests proving:
   - concurrent requests do not duplicate or partially initialize data
   - existing datasets are never silently replaced
   - sequential retry after successful initialization is safe
   - actual row count matches expectation
4. Review UI initialization state; decide whether indeterminate
   `Initializing…` is sufficient or whether bounded progress/status feedback
   is warranted. Do not introduce a background-job architecture.

### B. Real Docker-stack browser acceptance

**File to update:**
- `frontend/e2e/default-stack.spec.ts`

**Problem:** The current suite mostly checks page loading. It does not exercise
actual product behavior end-to-end.

**Required coverage:**
- start from an empty database
- reach initialization through the UI
- complete initialization through the UI
- see the experiment catalog become usable
- launch an actual experiment
- observe in-app execution state/progress
- open the completed run
- retrieve its artifacts
- open an execution plan where available
- access run history after execution
- compare compatible runs and handle invalid comparisons
- verify cancellation and failure diagnostics through real app paths

Constraints:
- Use default unauthenticated Docker Compose profile only.
- Do not seed the database via API before the browser test if that bypasses
  the first-run UI workflow.
- Keep workloads bounded and deterministic for CI.
- Preserve existing manual live-verification evidence; do not repeat it.

### C. CI teardown

**File to update:**
- `.github/workflows/ci.yml`

**Problem:** `default-stack` teardown fails because `QF_DB_PASSWORD` is scoped
only to the startup step. Compose cannot interpolate the config during teardown.
The error is swallowed by `|| true`.

**Required fix:**
- Move shared Compose env vars to job-level `env:` so both startup and
  teardown can interpolate `docker-compose.yml`.
- Verify teardown succeeds in the next CI run.

### D. Security audit gate

**File to update:**
- `.github/workflows/ci.yml`

**Problem:** `npm audit --audit-level=high` uses `continue-on-error: true`,
so high-severity findings do not fail the job.

**Required action:**
- Inspect current frontend audit state.
- Either enforce the gate or document precisely why it remains advisory.

### E. Documentation

**Files to update:**
- `README.md`
- `TEST_RUN.md`
- `finalization.md`
- `docs/release-readiness.md`
- `.audit/DEFECT_REGISTER.md` if needed
- `docs/qa/CONVERGENCE_PLAN.md` if needed

**Required changes:**
- README: explain UI-based first-run dataset initialization as the normal
  onboarding flow. Move manual SQL/data-generator loading to an
  operator/benchmark alternative section.
- TEST_RUN.md: document the default-stack Docker acceptance job and current
  test counts.
- Clearly state that the default application is unauthenticated and
  local-development-only. Authentication and messaging remain deferred.
- Correct status and relationship of historical QA docs to the authoritative
  `finalization.md`.

---

## 4. Verification requirements

After completing the above:

1. Run backend tests and report actual count.
2. Run frontend unit tests and production build.
3. Run the complete default Docker Compose stack locally.
4. Run the real-stack browser acceptance suite.
5. Verify initialization starts from an empty database.
6. Verify all five experiment templates with documented evidence.
7. Inspect CI logs, including teardown, not just overall green status.
8. Confirm CI passes on the resulting commit.

Do not use mocked API tests as substitutes for Docker acceptance tests.
Do not claim live visual verification without actual browser observation.

---

## 5. Completion definition

Stop when all of the following are true:

- `finalization.md` is internally consistent and traceable to evidence.
- Dataset initialization is safe under concurrency and has regression tests.
- Default-stack Playwright exercises real product behavior end-to-end.
- CI teardown succeeds without swallowed errors.
- Security audit gate is either enforced or explicitly documented.
- Documentation describes the current default workflow accurately.
- CI passes on the final commit.

This is a maintenance/closure plan, not an architecture initiative.
