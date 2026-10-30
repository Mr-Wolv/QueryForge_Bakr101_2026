# Release-readiness evidence

## Current verified scope

The repository contains five experiment templates (QF-000 through QF-004), run orchestration,
bounded k6 process handling, run manifests and artifacts, comparison compatibility checks,
PostgreSQL plan parsing, SSE run events, and an Angular evidence browser. The authenticated local
deployment uses those existing components; it does not replace them with fixture APIs.

## Local workflow register

| Workflow | Entry point | Security | Automated evidence | Status |
| --- | --- | --- | --- | --- |
| View catalog/history/plans/artifacts | Angular routes and `/api/**` reads | authenticated viewer or operator | backend functional/security tests; frontend Playwright smoke specs | implemented |
| Create, start, cancel a run | Catalog/run detail controls and `/api/runs` | operator + CSRF | orchestrator/controller tests; authenticated-profile security test | implemented |
| Compare runs | Compare screen and `/api/comparisons` | operator + CSRF | `ComparisonServiceTest`; authenticated-profile security test | implemented |
| Observe run state | run-detail SSE subscription | authenticated session | orchestrator/event tests | implemented; real-stack acceptance remains a release gate |
| Local sign-in/out | SPA `/oauth2` route + Keycloak | viewer/operator | `ProductionSecurityConfigTest`, `LocalAuthenticatedSecurityConfigTest` | implemented |
| Operational email | Prometheus → Alertmanager → Mailpit | Compose-network only | Compose configuration and manual acceptance command required | configured; not yet release-verified |

## Known verification limits

The fixture-backed Playwright first-session suite proves browser behavior against deterministic
API contracts; it is not evidence of browser-to-Keycloak-to-backend-to-PostgreSQL integration.
The latter must be run against the authenticated Compose stack before asserting a release verdict.

Testcontainers PostgreSQL coverage is skipped when Docker is unavailable. This is reported as
skipped/blocked, not passed. The release verdict is therefore **not yet issued**; use the recorded
commands and captured output from a clean local environment as the final evidence.
