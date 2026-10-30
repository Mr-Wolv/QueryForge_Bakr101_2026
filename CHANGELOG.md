# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `PlanExplorer` frontend component: collapsible EXPLAIN plan tree view with collapsible nodes, node-type labels, metrics columns (actual time, rows, cost, buffers), raw view toggle, and copy button.
- `PlanNodeComponent`: recursive tree node renderer with unknown-node-type fallback badge (`UNKNOWN`) and raw text tooltip.
- `/api/artifacts/{artifactId}/plan` endpoint returning parsed plan JSON.
- `PlanParser` backend service: robust EXPLAIN plan parser handling parallel plans, joins, CTEs, materialization, wrapped lines, and unknown node types.
- `HintService`: deterministic, data-grounded optimization hints derived from plan node types and metrics.
- `ComparisonService` fingerprint accuracy: now uses `environmentFingerprint` from manifest instead of fallback labels.
- Responsive breakpoints at 374px, 767px, and 1023px across all frontend views.

### Changed
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

## [1.0.0] - 2026-10-30

### Added
- Baseline PostgreSQL performance workbench: backend engine, frontend shell, tests, docs.
- 5 experiment templates (QF-000 through QF-004).
- REST API for experiment orchestration, run history, comparison, export.
- ng2-charts v7 frontend charts.
- H2 runtime profile for local development.
- Pre-commit hook and cleanup script for generated artifacts.
