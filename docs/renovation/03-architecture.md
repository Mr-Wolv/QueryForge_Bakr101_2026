# 03 — Architecture

## 3.1 Logical Component Map

```
com.bakr.queryforge
  api/
    controller/           existing product + new experiment/run/artifact/compare controllers
    dto/                  request/response records, validation annotations
    exception/            ProblemDetail mapper, RFC 7807 envelope
    config/               CORS, OpenAPI, Actuator, Micrometer
  experiment/
    catalog/              ExperimentTemplate, ExperimentTemplateService
    orchestration/        ExperimentOrchestrator, ExperimentRun, state machine
    lifecycle/            RunState transitions, timeout policies
    policy/               Parameter validation, allow-lists, bounds
  runner/
    adapter/              RunnerAdapter interface
    k6/                   K6RunnerAdapter (existing, hardened)
    process/              Process lifecycle, stdout/stderr capture, watchdog
  artifact/
    storage/              ArtifactService, immutable write path, checksum validation
    manifest/             RunManifest schema, versioning
    parser/               PlanParser, metrics normalization
  measurement/
    normalize/            Metrics normalizer, percentile aggregation
    compare/              ComparisonService, compatibility rules, verdict builder
  service/                existing ProductService, ProductSpecs, Cursor, KeysetPage
  repo/                   existing JPA repositories
  entity/                 Product, Category
```

## 3.2 Runner Boundary

The runner may invoke only a named, registered benchmark adapter. Adapter parameters are typed and validated. The browser cannot submit a command. If the first implementation wraps existing scripts, the script name and invocation shape are fixed in code; user inputs become validated environment variables/arguments, never shell fragments.

Runner contract:
1. `execute(templateId, parameters, runId, timeout)` returns `RunnerResult` with exit code, stdout, stderr, duration.
2. Timeout is enforced by watchdog thread; process is destroyed on expiry.
3. Stdout/stderr captured to bounded buffers and persisted as artifacts.
4. No runner adapter accesses network, database credentials, or filesystem outside designated artifact directory.

## 3.3 Artifact Storage

For v1, persist run manifests, raw JSON, CSV, and plan captures as immutable artifacts beneath `benchmark/runs/{runId}/`.

```
benchmark/runs/{runId}/
  manifest.json
  metrics.json
  plan.json
  k6-summary.json
  k6-summary.csv
  runner-stdout.log
  runner-stderr.log
  report.html  (optional export)
```

Writes:
1. Run directory created atomically with manifest containing expected artifact list.
2. Each artifact written with `StandardOpenOption.CREATE_NEW` — failure aborts run.
3. After all artifacts written, manifest checksums finalized and marked immutable.
4. Artifact paths never exposed; access only via `/api/runs/{id}/artifacts/{artifactId}` with path-safe lookup.

## 3.4 Target Database

Keep PostgreSQL 17 as the initial baseline. Version bump must be a separate experiment because planner behavior and result numbers may change. Continue using migration that intentionally starts without secondary indexes; candidate index applied separately via allow-listed script.

## 3.5 Frontend Architecture

```
frontend/src/app/
  core/
    tokens/            design tokens, theme service
    api/               ApiService, interceptors, error models
    types/             shared TypeScript interfaces
  features/
    overview/          environment + latest run
    catalog/           experiment list + launch form
    runs/              run history + run detail
    compare/           baseline/candidate comparison
    plan/              plan explorer
  shared/
    components/        state-card, latency-chart, skeleton-card
    pipes/             unit formatting, date formatting
    directives/        focus-visible, aria-live
```

## 3.6 API Outline

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/experiments` | Registered template catalog. |
| GET | `/api/experiments/{id}` | Template and parameter contract. |
| GET | `/api/lab/readiness` | Target database and optional tool status. |
| POST | `/api/runs` | Create run from allow-listed template and validated parameter object. Supports `X-Client-Request-Id` for idempotency. |
| GET | `/api/runs` | Bounded, paginated run history. |
| GET | `/api/runs/{id}` | State and summary. |
| POST | `/api/runs/{id}/cancel` | Cancel run (only when safely implemented). |
| GET | `/api/runs/{id}/artifacts` | Artifact catalog. |
| GET | `/api/runs/{id}/artifacts/{artifactId}` | Artifact download with path-safe ID lookup. |
| POST | `/api/comparisons` | Compare existing run IDs and report compatibility. |
| GET | `/api/comparisons/{id}` | Stored comparison/report. |
| GET | `/api/runs/{id}/events` | Server-Sent Events for run state transitions and runner output. |

### 3.6.1 Error Envelope (RFC 7807)

All errors return `ProblemDetail`:
```json
{
  "type": "https://problem.org/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Parameter 'rate' must be between 1 and 1000.",
  "instance": "/api/runs",
  "errors": {
    "rate": ["must be between 1 and 1000"]
  }
}
```

Frontend interceptor maps this to typed error; error page renders `title` + `detail`.
