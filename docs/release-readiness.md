# Release-readiness evidence

**Verdict date:** 2026-11-02  
**Scope:** local Docker Compose deployment in the default (unauthenticated) profile, plus the
fixture-backed browser suite and the host benchmark tooling. The production-shaped TLS deployment
was **not** deployed and is not covered by this verdict. Authentication and messaging integrations
are deferred to a later phase.

## What is verified

The repository contains five experiment templates (QF-000 through QF-004), run orchestration,
bounded k6 process handling, run manifests and artifacts, comparison compatibility checks,
PostgreSQL plan parsing, SSE run events, and an Angular evidence browser. The default local
deployment uses those components without authentication or messaging.

| Layer | Command | Result | Evidence |
| --- | --- | --- | --- |
| Backend unit + functional + integration | `./mvnw -B -ntp test` | **212 tests, 0 failures, 0 errors, 0 skipped** | terminal output, 2026-11-02 |
| Frontend unit (Vitest + jsdom) | `npm test` (in `frontend/`) | **53 tests, 10 files, 0 failures** | terminal output, 2026-11-02 |
| Frontend production build | `npm run build` | **BUILD_OK**, bundle within budget | terminal output |
| Fixture-backed browser suite | `npm run e2e:ci` | **3/3 passed** (`ng serve` + stubbed API) | terminal output, 2026-11-02 |
| Real-stack browser acceptance | default Docker Compose profile (no auth) | **QF-000 ~35s SUCCEEDED**, **QF-001 ~59s SUCCEEDED**, artifacts, history, compare verified | browser + terminal output, 2026-11-02 |

The fixture suite and the real-stack suite are deliberately separate. Fixture tests prove UI
behavior against deterministic contracts; the default-profile Docker stack is evidence of the
integrated browser-to-backend-to-PostgreSQL path without authentication.

## Local workflow register

| Workflow | Entry point | Security | Automated evidence | Status |
| --- | --- | --- | --- | --- |
| View catalog/history/plans/artifacts | Angular routes and `/api/**` reads | default unauthenticated profile | backend functional/security tests; live browser verification | verified |
| Create, start, cancel a run | Catalog/run detail controls and `/api/runs` | default unauthenticated profile | orchestrator/controller tests; live browser create/start/SSE/cancel | verified |
| Compare runs | Compare screen and `/api/comparisons` | default unauthenticated profile | `ComparisonServiceTest`; live browser comparison test | verified |
| Observe run state | run-detail SSE subscription | default unauthenticated profile | named-event client tests; live browser RUNNING/log/CANCELLED assertions | verified |
| Dataset initialization | Catalog warning + `POST /api/lab/initialize` | default unauthenticated profile | `ReadinessControllerTest`; live browser empty-dataset init | verified |
| Viewer/operator checks | N/A — auth deferred | N/A | Deferred to authenticated phase | deferred |

## Deployment verification details

- **Stack:** `docker compose --profile app up -d --build`. Services: `db`, `backend`, `frontend`.
- **Ports:** UI `127.0.0.1:8080`, API `127.0.0.1:8081`, DB `127.0.0.1:54001`.
- **Session boundary:** default profile disables authentication (`authEnabled: false`, `operator: true`);
  every request runs as an unrestricted local user.
- **Benchmark runner:** k6 runs inside the backend container. No authentication token is required in
  the default profile; k6 writes artifacts to bind-mounted `benchmark/runs/`.
- **Backup/restore:** `pg_dump -Fc` was restored into an isolated `qf_restore_check` database;
  `products` count and id range matched the live database, and both had 4 public tables. The
  scratch database and dump were removed afterwards.
- **Restart persistence:** backend restart (image rebuild) and `docker restart` of PostgreSQL both
  preserved run history and evidence; live browser verified after restart. Hikari logged
  expected `Failed to validate connection ... connection has been closed` warnings and recovered.

## Known verification limits

- The production-shaped deployment (`docker-compose.production.yml`, TLS, external OIDC/SMTP) is
  configured but was not deployed against real infrastructure. It is outside this verdict.
- The local database currently contains **1,000,000 rows** (initialized via `POST /api/lab/initialize`
  or pre-seeded). Headline latency and throughput numbers in the README are historical evidence from
  the 1M-row benchmark datasets.
- The real-stack browser verification exercises QF-000 (~35s) and QF-001 (~59s) with artifacts,
  history, and compare. QF-002, QF-003, and QF-004 are structurally validated by backend tests and
  the experiment catalog API but were not executed live in this pass due to time constraints.
- Authentication, authorization, OIDC, messaging, SMTP, and notifications are deferred to a later
  phase and are not covered by this verdict.
- jsdom prints `HTMLCanvasElement.getContext()` warnings because the `canvas` npm package is not
  installed. Tests pass; the warnings are noise, not failures.
- Testcontainers PostgreSQL coverage skips rather than fails when Docker is unavailable. A skipped
  run is reported as blocked, never as passed.
