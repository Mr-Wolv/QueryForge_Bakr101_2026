# QueryForge Renovation — Gap Analysis

**Generated:** 2026-10-29  
**Source:** Deep-dive comparison of implementation vs. `docs/renovation/` plan (10 documents)  
**Status:** Implementation ~65% through Phase 1, ~60% through Phase 2

---

## Executive Summary

The codebase has a **strong foundation** — all 5 experiment templates registered, orchestrator with full lifecycle (watchdog, cancel, restart recovery), runner adapter pattern, comparison service with compatibility rules, plan parser, design tokens, Angular 22 shell with 7 routes, and 72 passing backend tests + 14 passing E2E tests.

**However, critical gaps block shippable v1:**

| Category | Critical Gaps | High Gaps | Medium Gaps |
|----------|---------------|-----------|-------------|
| **API Contract** | No `/api/experiments` controller (404s), RFC 7807 field-level validation missing | Comparison request format mismatch, artifact URL convention drift | Dual-format acceptance in ComparisonController |
| **Safety** | Unbounded stdout/stderr capture (DoS risk), no Jakarta Validation DTOs | Path traversal edge case in artifact resolve, actuator exposed without auth | CORS dev origins, no rate limiting |
| **Reproducibility** | RunManifest missing required fields (env fingerprint, templateVersion, artifact checksums), ArtifactService read-only (no atomic write) | Comparison fingerprint broken (always falls back to template+state+duration) | Manifest write silent failure |
| **Observability** | No SSE endpoint (`/api/runs/{id}/events`), no Testcontainers PG integration tests | Actuator/Prometheus not configured, no correlation IDs in logs | OpenAPI schema validation test missing |
| **Frontend Quality** | LatencyChart custom bars (not ng2-charts), PlanExplorer tree view missing, ThemeService missing, StateCard error state missing | No SSE client, no validated launch form, no SkeletonCard, hardcoded colors in experiments.component.css | Accessibility: skip link, aria-hidden on decorative SVGs, focus-visible global |

---

## 1. API Contract Drift — Frontend ↔ Backend Mismatches

### Missing Controller
**`/api/experiments` and `/api/experiments/{id}`** — Frontend `ApiService.getExperiments()` and `getExperiment(id)` call these endpoints. **No `@RestController` exists.** Experiment templates served via `ExperimentTemplateService` but not exposed via HTTP.

### Request/Response Format Mismatches

| Endpoint | Frontend Sends | Backend Expects | Plan Spec |
|----------|----------------|-----------------|-----------|
| `POST /api/comparisons` | `{ "a": "...", "b": "..." }` | Accepts both `{leftRunId,rightRunId}` AND `{a,b}` | `{ "leftRunId": "...", "rightRunId": "..." }` |
| `GET /api/runs/{id}/artifacts` | Not used (uses `/api/artifacts/{id}`) | Implemented in `RunHistoryController` | Required per 05-api-contract.md:100 |
| `POST /api/runs` | `parameters` as `Record<string,string>` (all strings) | `Map<String,String>` parsed with `Integer.parseInt()` | Validated parameter object per FR-004 |

### Missing Endpoints
- `GET /api/experiments` — **Catalog controller missing**
- `GET /api/experiments/{id}` — **Not implemented**
- `GET /api/runs/{id}/events` (SSE) — **Not implemented** (FR-009, Phase 2 exit criteria)
- `POST /api/runs/{id}/cancel` — Implemented but **frontend never calls it**

---

## 2. Type System & Validation Gaps

### Backend — No Jakarta Validation DTOs

```java
// ExperimentRunController.create() signature:
public ResponseEntity<ExperimentRun> create(@RequestBody ExperimentRunRequest request)

// ExperimentRunRequest (record, no validation annotations):
public record ExperimentRunRequest(
    String templateId,
    Map<String, String> parameters,
    String clientRequestId
) {}

// PROBLEMS:
// - No @NotNull on templateId
// - No @Valid on parameters map
// - No @Size(min=1) on parameters
// - Validation happens in Orchestrator.validateParameters() — AFTER controller binding
// - Field-level errors never reach RFC 7807 ProblemDetail.errors map
```

### Frontend — No Client-Side Validation

```typescript
// ExperimentsComponent.launch() builds parameters from template defaults:
for (const p of template.parameters) {
  if (p.min !== undefined) parameters[p.name] = String(p.min);
  else if (p.allowed && p.allowed.length) parameters[p.name] = p.allowed[0];
}
// - No check that required parameters provided
// - No type coercion (integer vs string vs duration)
// - Sends "10" for integer field RATE — backend parses with Integer.parseInt()
```

---

## 3. Security Vulnerabilities

| Vector | Location | Risk | Details |
|--------|----------|------|---------|
| **Path traversal** | `ArtifactService.resolve()` | Medium | Checks `contains("..")` but `normalize()` called AFTER resolution; `benchmark/results/../../etc/passwd` could bypass if not caught early |
| **Unbounded process output** | `K6RunnerAdapter.execute()` | High | `redirectErrorStream(true)` merges stderr→stdout, **no bounded capture** — runaway process OOMs host |
| **OUT parameter injection** | `K6RunnerAdapter.locateSummary()` | Low | `out` used in `workDir.resolve(out + ".json")` — if `out` contains `../`, escapes workDir. ArtifactService protects download but not write. |
| **Missing rate limiting** | All endpoints | Medium | No Resilience4j, no `@RateLimiter` |
| **Actuator exposed** | `application.properties` | High | No `management.endpoints.web.exposure.include` — if prometheus added, metrics on `:8080/actuator/prometheus` without auth |

---

## 4. Race Conditions & Concurrency Bugs

### ExperimentOrchestrator — `activeRuns` Map Unsynchronized

```java
private final Map<String, ExperimentRun> activeRuns = new ConcurrentHashMap<>();
private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

// BUG 1: createRun() puts QUEUED run in activeRuns
// run() replaces with RUNNING — but if cancel() called BETWEEN create and run start?
// cancel() finds QUEUED run, destroys process (null), marks CANCELLED
// But run() may still be starting — no synchronization on runId

// BUG 2: writeManifest() in finally block — if process crashes before manifest write,
// activeRuns has RUNNING state, manifest never written. Restart recovery only scans benchmark/runs/*.json

// BUG 3: activeRuns NEVER cleaned up — terminal runs (SUCCEEDED, FAILED, CANCELLED, TIMED_OUT) accumulate forever → memory leak
```

### K6RunnerAdapter — Orphaned Process Risk

```java
// execute() returns Process — caller responsible for waitFor()
// If orchestrator crashes after execute() but before waitFor():
// - Process becomes orphaned (parent died)
// - watchdog scheduled but may not fire if JVM exits
// - No process group termination (destroyForcibly() only kills direct child)
```

### SSE Endpoint Missing — But Frontend Expects Real-Time

Plan 05-api-contract.md:176-192 specifies `/api/runs/{id}/events` with:
- `event: state` — state transitions
- `event: log` — runner stdout/stderr
- `event: artifact` — artifact ready
- `event: error` — runner error

**Without SSE:** polling only, no live log pane, no auto-scroll (Phase 2 exit criteria: "SSE events update UI in real time")

---

## 5. Data Integrity Gaps

### RunManifest — Missing Required Fields (Plan 03-architecture.md:47-57)

| Plan Required | Current | Impact |
|---------------|---------|--------|
| `templateVersion` | ❌ Missing | Cannot verify template compatibility |
| `parameters` (full validated object) | Partial (in labels) | Cannot reproduce exact run |
| `environmentFingerprint` | ❌ Missing | **Comparison fingerprint broken** |
| `startedAt` / `endedAt` | `startedAt` only | No end timestamp |
| `summaryMetrics` | ❌ Missing | Metrics in `ExperimentRun` but not manifest |
| `artifactChecksums` | ❌ Missing | Only manifest-level `checksumSha256` |
| `outcome` | ✅ | |
| `migrationVersion` | ✅ Hardcoded `"V1"` | |
| `indexState` | ✅ Hardcoded `"baseline"` | |
| `resourceLimits` | ✅ CPU/memory | |
| `appCommit` | ✅ From `GIT_COMMIT` env | |

### ArtifactService — Read-Only, No Atomic Write (Plan 10-manifest:7-8)

```java
// Current: only READS from benchmark/results/ and docs/experiments/plans/
// Phase 2 REQUIRES: write to benchmark/runs/{runId}/ with:
// - Files.createDirectories(workDir)
// - Each artifact written with StandardOpenOption.CREATE_NEW (failure aborts run)
// - Manifest checksums finalized after all artifacts written
// - Artifact paths never exposed; access only via /api/runs/{id}/artifacts/{artifactId}
```

### ComparisonService — Fingerprint Comparison Broken

```java
private String fingerprintOf(ExperimentRun run) {
    Path manifestPath = Path.of("benchmark", "runs", run.id() + ".json");
    // READS manifest from disk — but ExperimentOrchestrator.writeManifest()
    // DOESN'T include environmentFingerprint field!
    // So fingerprintOf() ALWAYS falls back to:
    return run.templateId() + "|" + run.state() + "|" + run.durationMs();
    // This makes ALL comparisons "compatible" unless template differs
}
```

**Result:** `ComparisonService.compare()` reports `compatible: true` for runs with different DB versions, dataset sizes, index states — violating FR-005.

---

## 6. Plan Parser Robustness — Edge Cases Missed

**Current test coverage:** Basic nodes, nesting, unknown types, execution time, Gather Merge, Index Only Scan, Buffers/Heap Blocks.

**Missing (real PostgreSQL plans):**
- `Parallel Seq Scan` with `Workers Planned/Launched`
- `Partial Aggregate` / `Finalize Aggregate` (parallel query)
- `Hash Join` / `Merge Join` with `Hash Cond` / `Merge Cond`
- `Bitmap Heap Scan` / `Bitmap Index Scan` with `Recheck Cond`
- `CTE Scan` / `Subquery Scan` / `Function Scan`
- `Materialize` / `Memoize` nodes
- `Incremental Sort` (PG 16+)
- `Gather` (not Gather Merge) for parallel plans
- `Result` node with `One-Time Filter`
- `LockRows` / `ModifyTable` (DML plans)
- Multi-line node details (wrapped lines in pgAdmin output)
- `Settings:` / `Planning Time:` / `Execution Time:` footer lines

**Current parser failure mode:**
```
->  Parallel Seq Scan on products  (cost=0.00..100.00 rows=1000 width=64)
     Workers Planned: 4
     Workers Launched: 4
```
The `Workers Planned:` line becomes a child node with type `"Workers Planned:"` — corrupts tree.

---

## 7. Comparison Logic Gaps

### Compatibility Rules (FR-005) — Most Not Implemented

| Rule | Implemented? | Gap |
|------|--------------|-----|
| Same experiment template | ✅ | |
| Both runs SUCCEEDED | ✅ | |
| Same DB version | ❌ | Fingerprint missing in manifest |
| Same dataset size | ❌ | Not extracted from parameters |
| Same workload type | ❌ | Not extracted from parameters |
| Same index state | ❌ | Hardcoded "baseline" in manifest |
| Same environment variables | ❌ | Not captured |

### Export Report — Missing Required Sections (FR-010)

**Current `ExportService.exportMarkdown()` produces:**
```markdown
# Experiment Run: <id>
| Field | Value |
| Template | ... |
| State | ... |
| Started | ... |
| Duration | ... |

## Hypothesis
<template.hypothesis>

## Metrics
| Metric | Value |
| Duration | ...ms |
| Iterations | ... |
| RPS | ... |

### Iteration Duration
| Percentile | ms |

### HTTP Request Duration
| Percentile | ms |

## Artifacts
- **kind** (type): location

## Labels
- key = value
```

**Plan requires:** Environment, Hypothesis, Method, Result, Limits, Trade-offs, Reproduction details, Compatibility statement (for comparisons).

---

## 8. Test Quality Gaps

| Test Layer | Plan Target | Current | Gap |
|------------|-------------|---------|-----|
| **Backend Unit** | 40+ specs | ~10 test classes | Missing: parameter validation (all templates), ArtifactService path traversal, PlanParser unknown nodes, ComparisonService compatible verdict, Metrics normalizer, Cursor/KeysetPage |
| **Backend Integration** | 20-30 (Testcontainers PG) | 1 (H2 only) | **Zero Testcontainers PostgreSQL tests** — critical for NFR-001 |
| **Frontend Unit** | 4 component tests | 0 | No component tests at all |
| **E2E** | 5-8 smoke specs | 7 smoke + 4 screenshots + 3 a11y | Missing: theme toggle persistence, artifact download verification, error state rendering |
| **Regression** | 5 scripts | 0 automated | No CI job: fixed seed reproduction, plan capture validation, dataset row counts, index state isolation, metrics↔raw artifact matching |

**Flaky Test Pattern:**
```java
// ExperimentOrchestratorCancelTimeoutTest uses reflection to inject into private activeRuns
Field activeRunsField = ExperimentOrchestrator.class.getDeclaredField("activeRuns");
activeRunsField.setAccessible(true);
// Brittle — breaks if field renamed
```

---

## 9. Frontend Architecture Deep Issues

### No Centralized State Management
- Each component injects `ApiService` and subscribes independently
- No caching — `getRuns()` called by Overview, Runs, Compare, RunDetail
- No request deduplication — rapid navigation causes duplicate HTTP requests
- No optimistic updates — launch button shows "Launching…" but no rollback on error

### Component Coupling Violations

```typescript
// RunDetailComponent & PlanComponent BOTH hardcode artifact ID suffix:
this.api.getArtifact(id + '-explain')
this.api.getArtifactMeta(id + '-explain')
// Artifact ID convention leaking into components — should be in ApiService
```

### LatencyChart — Custom Implementation Instead of ng2-charts

```typescript
// Current: custom CSS bar chart with hardcoded colors ['#111', '#333', '#555', '#777', '#999']
// Plan requires: Chart.js + ng2-charts with p50/p95/p99 datasets, axes labels, tooltips, responsive
// Dependencies INSTALLED (chart.js^4.4.7, ng2-charts^7.0.0) but NOT USED
```

### PlanComponent — Raw View Only

```typescript
// Template: <pre class="raw">{{plan}}</pre>
// Plan 04-frontend-design-system.md:132-147 REQUIRES:
// - Tree view with collapsible nodes
// - Node type uppercase label + monospace + icon
// - Actual rows / Plan rows / Rows removed / Buffer reads / Cost
// - Raw view toggle with copy button
// - Unknown node types → "UNKNOWN" label + raw JSON in tooltip
```

### Missing Components Per Plan

| Component | Plan Spec | Status |
|-----------|-----------|--------|
| `ThemeService` | Toggle dark/light, persist localStorage, apply `data-theme` | ❌ Not implemented |
| `SkeletonCard` | Shimmer loader with loading/error variants | ❌ Not implemented |
| `PlanExplorer` (tree) | Collapsible nodes, raw toggle, copy button | ❌ Not implemented |
| SSE Client | Live log pane, auto-scroll, pause button | ❌ Not implemented |
| Launch Form | Validated parameter controls per template | ❌ ExperimentsComponent uses min/first allowed |

### Accessibility Gaps

```css
/* StateCard — no 'error' state CSS */
.unavailable { border-color: #F9DED8; }  // amber-tinted per spec
/* MISSING: .error { border-color: var(--danger); background: var(--danger-bg); } */

/* No skip link in app.html — plan requires: "Skip to main content" as first focusable */
/* Focus visible uses --color-focus but no :focus-visible selector in global CSS */
/* Inline SVGs in StateCard have no aria-hidden="true" — announced by screen readers */
```

---

## 10. Architecture Drift from Plan

### Package Structure (Plan 03-architecture.md:5-31 vs Actual)

| Plan Package | Actual | Drift |
|--------------|--------|-------|
| `api/controller` | `controller/` (flat) | Controllers mixed: Product, Category, ExperimentRun, RunHistory, Artifact, Comparison, Hint, Readiness, ExperimentCatalog (missing) |
| `api/dto` | ❌ Missing | No DTO package — records used directly in controllers |
| `api/exception` | `controller/ApiExceptionHandler.java` | Single file, not package |
| `experiment/catalog` | ❌ | `ExperimentTemplateService` in `experiment/` root |
| `experiment/orchestration` | ❌ | `ExperimentOrchestrator`, `ExperimentRun`, `ExperimentRunState` in root |
| `experiment/lifecycle` | ❌ | State machine inside `ExperimentOrchestrator` |
| `experiment/policy` | ❌ | Validation inside `ExperimentOrchestrator.validateParameters()` |
| `runner/adapter` | `experiment/runner/RunnerAdapter.java` | Wrong package |
| `runner/k6` | `experiment/runner/K6RunnerAdapter.java` | Wrong package |
| `runner/process` | ❌ | Process lifecycle inside `ExperimentOrchestrator.run()` |
| `artifact/storage` | ❌ | `ArtifactService` in root — read-only |
| `artifact/manifest` | ❌ | **RunManifest.java MISSING** |
| `measurement/normalize` | ❌ | `Metrics.fromK6Node()` in `Metrics` record |
| `measurement/compare` | ❌ | `ComparisonService` in `experiment/` root |

### Runner Boundary Violations (Plan 03-architecture.md:33-41)

```java
// K6RunnerAdapter.resolveScript() HARDCODES script paths:
case "QF-001" -> Path.of("benchmark", "k6", "search.js");

// Plan: "Adapter parameters are typed and validated. The browser cannot submit a command.
// If the first implementation wraps existing scripts, the script name and invocation shape
// are fixed in code; user inputs become validated environment variables/arguments, never shell fragments."

// VIOLATION: Script path resolution hardcoded in adapter, not configurable per template
// VIOLATION: No ProcessBuilder.directory() isolation — uses workDir but no chroot/jail
// VIOLATION: No stdout/stderr bounded capture — plan requires "bounded buffers"
```

---

## 11. Observability Gaps

| Requirement | Plan | Current |
|-------------|------|---------|
| Actuator health endpoint | Exposed & secured to localhost | `management.endpoints.web.exposure.include` NOT SET — defaults to `health,info` only |
| Prometheus metrics | `micrometer-registry-prometheus` dep added | No `/actuator/prometheus` exposure configured |
| OpenAPI at `/v3/api-docs` | Required | `springdoc-openapi-starter-webmvc-ui` added but no verification test |
| Request correlation ID | `X-Request-Id` header | Frontend interceptor injects, backend reads `javax.servlet.error.request_uri` but **no correlation ID in logs** |
| Structured logging | `structured-logging` pattern | Standard Spring Boot logging — no JSON, no trace IDs |
| Run duration metrics | Phase 2 exit criteria | No `@Timed` on orchestration methods |

---

## 12. Configuration Gaps

### Missing `ActuatorConfig` (Plan 10-manifest.md:16)
```java
// Plan requires: src/main/java/.../config/ActuatorConfig.java (20 lines)
// Exposes health, metrics, prometheus; secures to localhost
// Current: application.properties has NO management.* properties
```

### Missing `application.properties` Additions (Plan 10-manifest.md:17)
```properties
management.endpoints.web.exposure.include=health,metrics,prometheus,info
management.endpoint.health.show-details=always
management.prometheus.metrics.export.enabled=true
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

---

## 13. Documentation vs Implementation Gaps

| README Claim | Reality |
|--------------|---------|
| "Angular 22 evidence browser lets you inspect experiments, run history, and comparisons" | ✅ Mostly works but missing SSE, PlanExplorer tree, dark mode toggle |
| "Launch a run or inspect existing ones" | ⚠️ Launch uses min/first allowed params — no validated form |
| "Each run detail shows latency percentiles, throughput, hints, and downloadable artifacts" | ⚠️ Hints are hardcoded stubs; artifacts use `/api/artifacts/` not `/api/runs/{id}/artifacts/` |
| "Use Compare to pick two runs and get a compatibility verdict with rule-level pass/fail breakdowns" | ✅ Works but fingerprint comparison broken |
| "All E2E smoke tests pass: catalog → run → detail → compare → artifact download → keyboard navigation" | ✅ 7/7 pass but **catalog launch not tested** (no experiment execution in smoke tests) |

---

## 14. Edge Cases Unhandled

| Scenario | Current Behavior | Plan Requirement |
|----------|------------------|------------------|
| Duplicate `clientRequestId` on different template | Orchestrator rejects globally — should be per-template? | FR-003: "Duplicate start requests with same client request ID do not launch duplicate work" |
| Runner process hangs (no output, no exit) | Watchdog kills after timeout | ✅ Watchdog implemented |
| Runner produces massive stdout (>100MB) | Unbounded — OOM risk | NFR-003: "bounded stdout/stderr capture" |
| Manifest write fails mid-run | Silent catch — run marked in memory but not persisted | NFR-005: "process restart does not corrupt completed report" |
| Comparison of run with missing manifest | `fingerprintOf()` falls back to template+state+duration | FR-005: "Comparison warns when runs differ in ways that make results non-comparable" |
| Plan parser encounters PG 18 new node type | Renders as "unknown" type with raw JSON in tooltip | FR-007: "Parser tolerates unknown plan node types" |
| SSE client reconnection after network blip | Not implemented | FR-009: "SSE events update UI in real time" |
| Dark mode toggle with `prefers-color-scheme` | Not implemented | Plan 04: "Toggle dark mode, reload page, verify theme persists" |

---

## 15. Performance & Scalability Concerns

| Issue | Location | Impact |
|-------|----------|--------|
| `ExperimentTemplateService.loadRuns()` scans `benchmark/results/*.json` on every call | Called by `ExperimentRunService.findAll()` → Overview, Runs, Compare, RunDetail | O(N) filesystem scan per request; no caching |
| `ComparisonService.fingerprintOf()` reads manifest from disk for EACH comparison | Called during comparison | Disk I/O per comparison; no caching |
| `ArtifactService.readPlan()` caches in static `PLAN_CACHE` Map — **never evicted** | `ArtifactService.PLAN_CACHE` | Memory leak if many plans accessed |
| `ExperimentOrchestrator.activeRuns` — **never cleaned up** terminal runs | `activeRuns` Map grows unbounded | Memory leak over time |
| `RunHistoryController.list()` returns ALL runs unpaginated | Plan 05-api-contract.md:108 requires `page`, `size`, `state` params | Current: returns `List<ExperimentRun>` — no pagination |
| `K6RunnerAdapter` uses `redirectErrorStream(true)` — merges stderr, loses separation | Stdout/stderr not captured separately | Debugging harder; plan requires both captured |

---

## Summary: Deepest Gaps by Category

### 🔴 CRITICAL (Block Shippable v1)
1. **No `/api/experiments` controller** — catalog 404s
2. **No RFC 7807 field-level validation** — write endpoints unsafe
3. **No SSE endpoint** — Phase 2 exit criteria unmet
4. **No Testcontainers PG integration tests** — "clean checkout mvn test" fails on CI
5. **RunManifest missing required fields** — reproducibility broken (NFR-001)
6. **ArtifactService read-only** — no atomic write + checksums (NFR-001)
7. **LatencyChart not using ng2-charts** — editorial score < 9.5/10
8. **PlanExplorer tree view missing** — core differentiator absent

### 🟠 HIGH (Degrade Quality Significantly)
9. **ThemeService missing** — dark mode toggle broken
10. **Comparison fingerprint broken** — all runs appear compatible
11. **No Jakarta Validation DTOs** — validation at wrong layer
12. **Frontend API contract mismatches** — comparison format, artifact URLs
13. **Unbounded process output capture** — DoS risk
14. **No pagination on `/api/runs`** — scales poorly
15. **Memory leaks**: `PLAN_CACHE`, `activeRuns` never evicted

### 🟡 MEDIUM (Technical Debt)
16. Package structure drift from plan
17. Plan parser edge cases (parallel plans, wrapped lines)
18. Export report missing required sections
19. HintController returns hardcoded stubs
20. Actuator/Prometheus not configured
21. No structured logging / correlation IDs
22. Frontend: no request deduplication, no centralized state
23. Accessibility: missing error state, skip link, aria-hidden on decorative SVGs

### 🟢 LOW (Polish)
24. Hardcoded colors in `experiments.component.css` (not using tokens)
25. `ExperimentRunRequest` record not in `api/dto` package
26. `HintController` in wrong package per plan
27. ComparisonController dual format acceptance

---

## Recommended Execution Order (Critical Path)

### Week 1: Foundation & Safety
1. Add `ExperimentCatalogController` (`/api/experiments`, `/api/experiments/{id}`)
2. Create Jakarta Validation DTOs (`ExperimentRunRequest`, `CompareRequest`)
3. Fix `ApiExceptionHandler` + `ProblemDetailConfig` for RFC 7807 field errors
4. Implement `RunManifest` record with ALL required fields
5. Implement `ArtifactService` atomic write (`CREATE_NEW` + SHA-256)
6. Add `ActuatorConfig` + `management.*` properties
7. Add Testcontainers PostgreSQL integration test suite

### Week 2: Phase 1 Polish (Editorial 9.5/10)
8. Implement `ThemeService` + dark mode toggle in topbar
9. Replace `LatencyChart` with ng2-charts (p50/p95/p99, axes, tooltips)
10. Build `PlanExplorer` tree component (collapsible, raw toggle, copy)
11. Add `StateCard` error state + `SkeletonCard` component
12. Fix Experiment launch form with validated parameter controls
13. Responsive verification at 375/768/1024/1440px
14. axe-core + Playwright a11y test

### Week 3: Phase 2 Orchestration
15. Implement SSE endpoint (`/api/runs/{id}/events`) + `SseEmitter`
16. Frontend SSE client (`RunEventService`) + live log pane
17. Bounded stdout/stderr capture in `K6RunnerAdapter`
18. Fix comparison fingerprint (store env fingerprint in manifest)
19. Pagination on `/api/runs`
20. OpenAPI schema validation test

### Week 4: Phase 3 + Gate
21. Verify all 5 experiments produce artifacts via UI
22. Export button on Compare page + Run export includes method/hypothesis/limits/trade-offs
23. Evidence library filters (template, state, date)
24. Run all verification commands from `10-implementation-manifest.md:79-99`
25. User review ≥ 9.5/10 per phase

---

## Verification Commands (from 10-implementation-manifest.md)

```bash
# Phase 1 exit
mvn test
cd frontend && npm run build
cd frontend && npx playwright test
cd frontend && npx playwright test --project=chromium --grep "a11y"
grep -R "hard-coded" frontend/src || echo "No hard-coded values found"

# Phase 2 exit
curl -s localhost:8080/actuator/health | jq .
curl -s localhost:8080/v3/api-docs | jq '.paths | keys | length'
curl -s localhost:8080/api/lab/readiness | jq '.database.status'

# Phase 3 exit
grep -R "QF-00[0-4]" frontend/src/app/components/experiments/
curl -s -X POST localhost:8080/api/runs -H 'Content-Type: application/json' -d '{"templateId":"QF-001","parameters":{"rate":"10","duration":"60s"}}' | jq .

# Phase 4 (gated)
grep -R "hypothesis" frontend/src/app/components/compare/ || echo "No hints yet"
```