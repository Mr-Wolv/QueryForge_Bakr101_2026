# 07 — Phases and Gates

## 7.1 Phase 0 — Baseline and Design Freeze

**Duration:** 1–2 days  
**Goal:** Verify existing evidence is reproducible and lock the design.

Tasks:
1. Clean-checkout build and test verification on JDK 25 + Maven + Docker.
2. Inventory current scripts, experiment IDs (QF-000 through QF-004), artifacts, plan formats, and exact reproduction commands.
3. Verify README headline values map to specific experiment/artifact.
4. Author this plan, review with user, and freeze scope.

Exit Criteria:
- [ ] `mvn test` passes on clean checkout.
- [ ] One baseline and one optimized measurement reproducible from fresh database.
- [ ] Plan reviewed and scope boundaries accepted.
- [ ] No uncommitted changes beyond plan documents.

## 7.2 Phase 1 — Read-Only Evidence Browser

**Duration:** 3–5 days  
**Goal:** Angular shell + existing artifact inspection. Zero benchmark execution from UI.

Tasks:
1. Implement design tokens (`tokens.css`) and dark mode infrastructure.
2. Build responsive app shell (topbar, nav, main, skip link).
3. Implement `StateCardComponent` with loading, empty, unavailable, error states.
4. Build Overview page (readiness + latest run + baseline).
5. Build Experiment Catalog page (read-only list).
6. Build Run History page (bounded, paginated).
7. Build Run Detail page (metrics + artifacts).
8. Build Compare page (compatibility verdict + metric deltas).
9. Build Plan Explorer (tree + raw view).
10. Add `ng2-charts` integration with `LatencyChartComponent`.
11. Implement `ApiInterceptor` (ProblemDetail mapping, request ID injection).
12. Add skeleton loaders and route transition animations.

Exit Criteria:
- [ ] User can inspect existing experiments and results with no benchmark execution triggered from UI.
- [ ] Dark mode toggle works and persists.
- [ ] Charts render correct values with units.
- [ ] Responsive at 375px, 768px, 1024px, 1440px.
- [ ] Keyboard navigation works on all primary flows.
- [ ] `npx playwright test` passes all 5 E2E smoke specs.
- [ ] No new API executes work or mutates target DB.

## 7.3 Phase 2 — Safe Experiment Orchestration

**Duration:** 3–5 days  
**Goal:** Launch one experiment end-to-end through the UI.

Tasks:
1. Implement typed DTOs with Jakarta Validation for run creation.
2. Implement `ExperimentOrchestrator` with state machine, timeout watchdog, cancel (if safe), restart recovery.
3. Implement `RunnerAdapter` contract + `K6RunnerAdapter` hardening.
4. Implement `ArtifactService` atomic write path and checksum validation.
5. Implement SSE endpoint for run events.
6. Implement frontend SSE client with live log pane and progress indicator.
7. Add experiment launch form with validated parameter controls.
8. Wire one experiment (QF-001 filtered search) end-to-end.

Exit Criteria:
- [ ] One run can be launched through UI and produces complete evidence artifact.
- [ ] Invalid inputs, duplicate requests, timeouts, and runner failure tested.
- [ ] API remains responsive during workload (measured via actuator).
- [ ] SSE events update UI in real time.
- [ ] Cancel stops scheduling new work and terminates runner safely.

## 7.4 Phase 3 — Experiment Breadth and Comparison

**Duration:** 2–3 days  
**Goal:** Remaining experiments + comparison robustness.

Tasks:
1. Register remaining experiments (QF-002, QF-003, QF-004) in template registry.
2. Verify each produces expected artifacts and metrics.
3. Implement run compatibility rules and explicit "not comparable" behavior.
- blockers: different dataset size, different PostgreSQL major version, different index state, different query definition.
4. Implement HTML/Markdown export report.
5. Add evidence library filters (by template, state, date range).

Exit Criteria:
- [ ] Core existing experiments reproducible from UI.
- [ ] Incompatible comparisons blocked or prominently qualified.
- [ ] Historical README data traceable to original reproduction procedure.
- [ ] Export report includes method, hypothesis, results, limitations, trade-offs.

## 7.5 Phase 4 — Explainable Optimization Hints (Gated)

**Duration:** 2–3 days  
**Gate:** Phases 1–3 complete and core tool stable. User approval required.

Tasks:
1. Implement hint rules grounded in plan features (e.g., large filtered scan with low selectivity).
2. Each hint links to relevant plan nodes and proposed hypothesis.
3. Hint UI shows evidence, assumptions, and explicit "test this in lab" action.
4. Optional: apply proposed index in disposable lab with explicit confirmation, rerun experiment, show measured result.

Exit Criteria:
- [ ] Every suggestion has reason and linked evidence.
- [ ] No suggestion described as "optimal" without measured support.
- [ ] Human remains responsible for applying change.
