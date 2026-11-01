# QueryForge — Post-Release Next Steps

**Created:** 2026-11-01  
**Status:** Proposed — awaiting decision  
**Context:** Repo is at a deliverable local-dev state; full QA expansion is out of scope for this closure pass.

---

## Current State Snapshot

| Area | State |
|------|-------|
| Backend tests | 209/209 green (`./mvnw test`) |
| Frontend tests | 50/50 green (`npm test`) |
| Frontend build | Green (`npm run build:analyze`) — 2 warnings, 0 errors |
| Docker Compose | `docker compose --profile app up --build -d --wait` works; auth/profile `app` starts without Keycloak |
| Git history | Clean, 128+ commits, no noise, no uncommitted bloat |
| Docs | `AUDIT_FINDINGS.md` and `CONVERGENCE_PLAN.md` current |
| Remote push | Not performed — local-only delivery |

Known limitation: full-stack E2E and first-run UX flows are documented but not expanded into a full Playwright suite or new UI workflows.

---

## Proposed Closure Path (Recommended)

These three steps close the remaining verification gap without opening a new QA initiative.

### Step 1 — Docker Live Smoke Test

**Time:** 30–45 minutes  
**Risk:** Low  
**Command:**

```bash
docker compose --profile app up --build -d --wait
```

**Checks:**
- Frontend reachable at `http://localhost:8081/`
- Backend health at `http://localhost:8080/actuator/health`
- PostgreSQL reachable from backend container
- Core UI paths load: overview, experiment catalog, run history, run detail, compare, plan explorer
- No console errors in browser DevTools
- No container restarts or health flapping

**Pass criteria:** All core routes render; backend returns 200 on health and API probes; no obvious visual defects in shell navigation.

---

### Step 2 — Run Existing Playwright Suite Against Live Stack

**Time:** 30–60 minutes  
**Risk:** Low  
**Commands:**

```bash
# Frontend
cd frontend
npm run e2e:ci        # first-session.spec.ts — smoke paths
npm run e2e:real      # real-stack.spec.ts — authenticated/local stack paths if auth is re-enabled later
```

**Scope:**
- Use the existing `e2e/first-session.spec.ts` and `e2e/real-stack.spec.ts`
- Do not add new Playwright tests in this step
- Treat failures as bugs to fix, not as test flake to ignore

**Pass criteria:** All executed Playwright specs pass against the Docker-deployed application.

---

### Step 3 — Document Honest State

**Time:** 15 minutes  
**Risk:** None  
**Actions:**
- Update `docs/qa/CONVERGENCE_PLAN.md` with final test results
- Record any defects found in smoke/Playwright in `docs/qa/BUG_HUNT.md`
- Mark this closure path as completed in `AUDIT_FINDINGS.md` Phase 6

**Deliverable:** A defensible “ready for local-dev use” snapshot with evidence, not a theoretical plan.

---

## Deferred / Post-Release Scope

These are **explicitly out of scope** for the closure pass above. They should be tracked as follow-on tickets, not mixed into this delivery.

| Item | Why deferred |
|------|--------------|
| Expanded Playwright E2E suite | Full page-object + selector maintenance is a new workstream |
| First-run empty-state UX flows | Requires backend init endpoints + guided UI redesign |
| Local CI trial | Reproduces local commands in a container; proves nothing new |
| Full bug-hunt cycle per attached brief | Weeks of work, not a closure step |
| Authentication re-introduction | Explicitly dropped; local-dev only |
| k6/benchmark automation in CI | Bonus work, not on critical path |
| Accessibility audit expansion | Partial coverage exists; full WCAG 2.2 AA is a follow-on |
| Production-shaped TLS deployment | Scoped out; no infrastructure to validate against |

---

## Decision Points

1. **Proceed with Step 1–3 now?**  
   → Smoke test + existing Playwright + documentation.

2. **Expand E2E and first-run UX before declaring done?**  
   → This becomes a new project phase with separate time estimates.

3. **Accept current state as “ready with documented limitations”?**  
   → Tag the repo, archive the QA snapshot, move on.

---

## Final Verdict Options

| Verdict | Meaning |
|---------|---------|
| READY FOR FURTHER ACCEPTANCE TESTING | Core paths verified; known gaps are tracked, not hidden |
| READY WITH DOCUMENTED LIMITATIONS | Functional but missing first-run UX and expanded E2E |
| NOT READY — RELEASE BLOCKERS REMAIN | Smoke test or Playwright found a blocking defect |

**Current recommendation:** Target **READY WITH DOCUMENTED LIMITATIONS** after Steps 1–3, unless smoke testing reveals a blocking defect.
