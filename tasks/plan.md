# Implementation Plan: QueryForge v2 — 100% Completion

## Overview
Complete the remaining ~30-35% of the QueryForge Full-Stack Performance Workbench Renovation Plan. The backend experiment orchestration, artifact storage, plan parsing, and Angular shell are largely in place. The largest remaining gaps are: backend comparison API with compatibility rules, export report generation, UI charts, cancel/timeout enforcement, manifest completeness, E2E tests, and README screenshots.

## Architecture Decisions
- Keep backend changes minimal and additive; no breaking changes to existing API.
- Use client-side charting with a lightweight library or manual SVG to avoid heavy frontend deps.
- Implement comparison as backend-generated report + frontend renderer.
- Export reports as Markdown/HTML served via existing artifact/download pattern.
- Timeout enforcement via `ProcessBuilder` timeout or watchdog thread; cancel via process destroy.
- E2E via Playwright standalone (no Cypress to match existing deps).

## Task List

### Phase 0 — Baseline & Design Freeze (95% → 100%)
- [ ] Fix `ProductApiFunctionalTest` context-load failure by adding explicit datasource properties for testcontainers-less runs or ensuring Docker availability.
- [ ] Verify all README headline numbers have matching artifacts and reproduction commands.
- [ ] Add `TEST_RUN.md` documenting test execution, known Docker dependency, and manual verification steps.

### Phase 1 — Read-only Evidence Browser (80% → 100%)
- [ ] Add latency/throughput chart component to Angular run-detail and compare views.
- [ ] Add empty-state, loading-state, and unavailable-state components.
- [ ] Backend: add `GET /api/comparisons` lightweight stub for future compatibility logic.

### Phase 2 — Safe Experiment Orchestration (70% → 100%)
- [ ] Implement per-run timeout (hard limit via process watchdog).
- [ ] Implement `POST /api/runs/{id}/cancel` with safe process termination.
- [ ] Add TIMED_OUT and CANCELLED state transitions and tests.
- [ ] Add restart recovery logic: on startup, mark in-progress runs as FAILED unless resumable.

### Phase 3 — Experiment Breadth & Comparison (45% → 100%)
- [ ] Backend: `POST /api/comparisons` with compatibility rules (DB version, dataset size, load, query definition, index state).
- [ ] Backend: stored comparison report artifact.
- [ ] Frontend: compare view consumes backend compatibility verdict.
- [ ] Export report endpoint: `GET /api/runs/{id}/export?format=markdown|html`.
- [ ] Export comparison endpoint: `GET /api/comparisons/{id}/export?format=markdown|html`.
- [ ] Artifact library filtering in UI.

### Phase 4 — Explainable Advisor (0% → 100%)
- [ ] Backend: rule-based hint generator from plan features and workload metadata.
- [ ] Backend: `GET /api/runs/{id}/hints`.
- [ ] Frontend: hints panel in run detail.
- [ ] Frontend: apply-index flow in disposable lab with explicit confirmation.

### NFRs & Polish (60% → 100%)
- [ ] E2E smoke test: Playwright script for catalog → run → detail → compare → artifact download.
- [ ] README: add 3-4 UI screenshots.
- [ ] Tests: path-traversal rejection for artifact controller.
- [ ] Tests: restart recovery behavior.
- [ ] Tests: parameter validation edge cases.
- [ ] Manifests: add migration version, index state, hardware/resource limits, app commit.

## Parallelization
- Frontend charting and states can proceed in parallel with backend comparison/export.
- Advisor can start once hints backend is done, independent of frontend charting.
- E2E tests last, after all features stable.

## Risks
| Risk | Impact | Mitigation |
|------|--------|------------|
| Docker unavailable for functional tests locally | High | Use `QF_DB_URL` override and document Windows Testcontainers workaround in TEST_RUN.md |
| Scope too large for one session | High | Break into kanban cards; prioritize Phase 3 comparison/export as highest value |
| Frontend chart library choice | Med | Use lightweight manual SVG or Canvas to avoid dependency bloat |
