# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Docker-first Compose startup for the database, API, and frontend, including fresh-volume schema initialization and an isolated Python dataset-generator service.
- Portable SQL dataset loader that refreshes PostgreSQL statistics and advances the product ID sequence after import.
- `ManifestStore` + `RunArtifact`: immutable per-run manifests with checksums, and run-scoped artifact IDs so downloads resolve through `/api/runs/{id}/artifacts/{artifactId}` instead of a global artifact name space.
- Paginated run history: `GET /api/runs` returns a `PageResponse` with `state` filtering; the frontend consumes the enveloped list.
- `EnvironmentFingerprintService`, `MetricDelta`, `RunEventBus`, `RunConflictException`, `ParameterValidationException` and `DuplicateRequestException` for fingerprints, metric deltas, SSE fan-out, and RFC 7807 conflict/validation responses.
- `PostgresIntegrationSmokeTest`: starts `postgres:17` through Testcontainers, applies the operator migration, and runs a filtered-search query (skips automatically without a Docker daemon).
- Run detail links `explain-plan` artifacts to **View plan**, wiring the parsed plan tree into the UI.
- `PlanExplorer` frontend component: collapsible EXPLAIN plan tree view with collapsible nodes, node-type labels, metrics columns (actual time, rows, cost, buffers), raw view toggle, and copy button.
- `PlanNodeComponent`: recursive tree node renderer with unknown-node-type fallback badge (`UNKNOWN`) and raw text tooltip.
- `/api/artifacts/{artifactId}/plan` endpoint returning parsed plan JSON.
- `PlanParser` backend service: robust EXPLAIN plan parser handling parallel plans, joins, CTEs, materialization, wrapped lines, and unknown node types.
- `HintService`: deterministic, data-grounded optimization hints derived from plan node types and metrics.
- `ComparisonService` fingerprint accuracy: now uses `environmentFingerprint` from manifest instead of fallback labels.
- Responsive breakpoints at 374px, 767px, and 1023px across all frontend views.
- Compare view renders the backend's `metricsDelta`: a grouped metric-delta table (baseline, candidate, absolute change and percentage) colour-coded as improvement or regression, plus explicit blocker and warning lists and a `vs` run heading.
- Theme toggle uses an SVG sun/moon icon instead of a text glyph, and the topbar is theme-aware.
- Latency chart bars carry value labels and follow the design tokens in both themes.

### Changed
- Angular pages load as route-level chunks; the production initial-bundle budget is now 400 kB warning / 500 kB error.
- Run-history requests reuse an in-memory index of persisted manifests rather than reopening every manifest file for each request.
- Frontend container build now uses Node.js 24 to match CI and contributor setup.
- `ExperimentRun` record now has dedicated `parameters` field; manifest `parameters` populated from run input parameters instead of labels.
- `HintController` returns deterministic hints via `HintService` instead of hardcoded stubs.
- `ArtifactService.PLAN_CACHE` bounded to 64 entries with LRU eviction.
- `ExperimentOrchestrator.activeRuns` cleaned up on terminal state transitions.
- `ExperimentTemplateService` `parseManifest()` reads `parameters` from stored JSON.

### Fixed
- Manifest population now distinguishes `parameters` from `labels`.
- Memory leak in `ArtifactService.PLAN_CACHE` (unbounded growth).
- Memory leak in `ExperimentOrchestrator.activeRuns` (terminal entries never evicted).
- `ExperimentRun` record signature updated in all constructors.
- `ApiExceptionHandler` reported unmapped paths as `500 Internal Server Error`; unknown routes now return the RFC 7807 `404` envelope.
- `PlanExplorerComponent` never rendered its tree: it mutated state in an HTTP callback without scheduling change detection, so it stayed invisible in the zoneless Angular app.
- Dark/light theming: token families were split between `--text-*` and `--color-*`, leaving low-contrast text in one theme; tokens are unified and both themes are legible.
- Run detail never rendered optimization hints, and its export button placement and empty-state spacing were off.
- Run history table now shows its column header row, the missing p95 value, and a cancelled-state badge.
- Experiment catalog cards had uneven heights and linked to the wrong target.
- Leftover cancelled test runs were removed from the demo data (`benchmark/runs`).
- `pom.xml` pins the Docker Engine API version (`api.version=1.44`) so Testcontainers can reach Docker Engine 25+.

## [1.0.0] - 2026-10-30

### Added
- Baseline PostgreSQL performance workbench: backend engine, frontend shell, tests, docs.
- 5 experiment templates (QF-000 through QF-004).
- REST API for experiment orchestration, run history, comparison, export.
- ng2-charts v7 frontend charts.
- H2 runtime profile for local development.
- Pre-commit hook and cleanup script for generated artifacts.
