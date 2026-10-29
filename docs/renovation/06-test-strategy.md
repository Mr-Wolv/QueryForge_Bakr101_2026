# 06 — Test Strategy

## 6.1 Test Pyramid

```
        /\
       /  \      E2E (Playwright) — 5–8 smoke specs
      /____\
     /      \   Integration (SpringBootTest + Testcontainers) — 20–30 specs
    /__________\
   /            \ Unit (JUnit 5) — 40+ specs
```

## 6.2 Backend Unit Tests

Target: 40+ specs covering:
- Parameter validation and bounds (every template parameter)
- ExperimentTemplateService: lookup, unknown ID
- ExperimentOrchestrator: create run, duplicate client request id rejection, timeout, cancel, restart recovery
- ArtifactService: atomic write, path traversal rejection, checksum verification
- PlanParser: known node types, unknown node types, missing fields
- ComparisonService: compatible verdict, incompatible verdict, warning generation
- Metrics normalizer: percentile aggregation, missing values
- Cursor/KeysetPage: encoding/decoding, boundary conditions

Coverage gate: 80% line coverage for `experiment/`, `artifact/`, `measurement/` packages.

## 6.3 Backend Integration Tests

Target: 20–30 specs using Testcontainers PostgreSQL.

- `ProductApiFunctionalTest` (existing) — keep and extend.
- Experiment lifecycle: create run, poll state, verify artifacts exist on terminal state.
- Runner contract: fake executable returns success, nonzero exit, timeout, partial output.
- Artifact persistence: restart recovery simulation, manifest immutability.
- OpenAPI schema validation: generated schema matches expected contracts.
- Actuator endpoints: health, prometheus, info expose expected metrics.

## 6.4 Frontend Unit Tests

Target: component tests for:
- `StateCardComponent`: all four states render correctly.
- `LatencyChartComponent`: renders datasets, handles missing values, responsive resize.
- `ApiInterceptor`: maps ProblemDetail to typed error, injects request ID.
- `ThemeService`: toggle persists to localStorage, applies data-theme.

## 6.5 E2E Tests (Playwright)

Target: 5–8 smoke specs covering critical user journeys on a small safe dataset (10K rows, 30s duration mocks).

1. **Catalog → Launch → Detail:** Open catalog, launch QF-001, wait for succeeded, open detail, verify metrics and artifacts visible.
2. **Compare Compatible:** Select two compatible runs, verify COMPATIBLE verdict and metric deltas.
3. **Compare Incompatible:** Select runs with mismatched dataset size, verify INCOMPATIBLE verdict and blockers.
4. **Plan Explorer:** Open run with plan artifact, expand tree nodes, switch raw view, verify copy button.
5. **Accessibility:** Keyboard navigation through catalog, runs, compare. Focus visible on all interactive elements.
6. **Theme Toggle:** Toggle dark mode, reload page, verify theme persists.
7. **Artifact Download:** Download manifest.json, verify content.
8. **Error State:** Trigger invalid parameter, verify ProblemDetail rendered.

## 6.6 Regression Tests

Scripts that must always pass:
- Fixed seed generation reproduces same dataset (row count, idempotent values).
- Plan capture produces valid JSON for known workload.
- Dataset row count matches expected value at 10K, 100K, 1M.
- Index state isolated: baseline schema has zero secondary indexes.
- Reported metrics match raw artifact values (no rounding or interpolation in stored manifests).
