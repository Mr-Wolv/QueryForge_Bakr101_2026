# Release-readiness evidence

**Verdict date:** 2026-10-31
**Scope:** local Docker Compose deployment in the authenticated (`local-auth`) profile, plus the
fixture-backed browser suite and the host benchmark tooling. The production-shaped TLS deployment
was **not** deployed and is not covered by this verdict.

## What is verified

The repository contains five experiment templates (QF-000 through QF-004), run orchestration,
bounded k6 process handling, run manifests and artifacts, comparison compatibility checks,
PostgreSQL plan parsing, SSE run events, and an Angular evidence browser. The authenticated local
deployment uses those components; it does not replace them with fixture APIs.

| Layer | Command | Result | Evidence |
| --- | --- | --- | --- |
| Backend unit + functional + PostgreSQL integration | `runmvn.bat -B -ntp clean verify` | **143 tests, 0 failures, 0 errors, 0 skipped** (`postgres:17` via Testcontainers) | `.audit/verify_run4.log` |
| Frontend unit (Vitest + jsdom) | `npm test` (in `frontend/`) | **23 tests, 6 files, 0 failures** | terminal output, 2026-10-31 03:43 |
| Frontend production build | `npm run build` | **BUILD_OK**, 351.99 kB initial / 96.52 kB transfer | terminal output |
| Fixture-backed browser suite | `npm run e2e:ci` | **3/3 passed** (`ng serve` + stubbed API) | `.audit/e2e_fixture_run2.log` |
| Real-stack browser acceptance | `QF_REAL_STACK=1 PLAYWRIGHT_BASE_URL=http://localhost:8080 npx playwright test e2e/real-stack.spec.ts` | **9/9 passed** (nginx → backend → PostgreSQL → Keycloak → k6) | `.audit/e2e_real_stack_run7_final.log` |

The fixture suite and the real-stack suite are deliberately separate. Fixture tests prove UI
behavior against deterministic contracts; only `real-stack.spec.ts` is evidence of the integrated
browser-to-Keycloak-to-backend-to-PostgreSQL path.

## Local workflow register

| Workflow | Entry point | Security | Automated evidence | Status |
| --- | --- | --- | --- | --- |
| View catalog/history/plans/artifacts | Angular routes and `/api/**` reads | authenticated viewer or operator | backend functional/security tests; real-stack RBAC and plan tests | verified |
| Create, start, cancel a run | Catalog/run detail controls and `/api/runs` | operator + CSRF | orchestrator/controller tests; real-stack create/start/SSE/cancel test | verified |
| Compare runs | Compare screen and `/api/comparisons` | operator + CSRF | `ComparisonServiceTest`; real-stack comparison test | verified |
| Observe run state | run-detail SSE subscription | authenticated session | named-event client tests; real-stack live RUNNING/log/CANCELLED assertions | verified |
| Local sign-in/out | SPA header + `/oauth2/authorization/queryforge`; `/api/auth/logout` | viewer/operator; CSRF on logout | `ProductionSecurityConfigTest`, `LocalAuthenticatedSecurityConfigTest`, real-stack sign-out test (plus 4 repeats) | verified |
| Viewer cannot mutate | API and UI | server-side role check | real-stack: 403 `Operator access required` with a valid CSRF token; no Start button | verified |
| CSRF rejection | `/api/runs` without token | server-side CSRF filter | real-stack: 403 `CSRF token missing or invalid` | verified |
| Malformed input | `/api/runs` with truncated JSON | RFC 7807 envelope | real-stack: 400 `Malformed Request Body`; `ApiExceptionHandlerTest` | verified |
| Operational email | Prometheus → Alertmanager → Mailpit | Compose-network only | forced backend outage produced `[FIRING:1] QueryForgeBackendUnavailable` and `[RESOLVED]` mail in Mailpit | verified |
| Metrics scrape | Prometheus targets | internal network | `queryforge` and `postgres` targets both `up` after rebuilds/restarts | verified |
| Backup/restore | `pg_dump -Fc` → `pg_restore` into a scratch DB | local only | 250 products, ids 1–250, 4 public tables in both databases | verified |
| Restart persistence | restart backend and PostgreSQL | local only | real-stack suite passed after both restarts; Hikari reconnected automatically | verified |

## Deployment verification details

- **Stack:** `QF_SPRING_PROFILE=local-auth docker compose --profile app --profile authenticated
  --profile monitoring up -d --build`. Services: `db`, `backend`, `frontend`, `keycloak`,
  `mailpit`, `postgres-exporter`, `prometheus`, `alertmanager`.
- **Ports:** UI `127.0.0.1:8080`, API `127.0.0.1:8081`, Mailpit `127.0.0.1:8025`, Prometheus
  `127.0.0.1:9090`. Keycloak and Alertmanager stay internal (reached through nginx `/id/`).
- **Accounts:** `viewer` / `viewer-local-only` (read-only), `operator` / `operator-local-only`
  (read + run control), Keycloak admin `admin` / `admin-local-only`. All are local fixtures.
- **Session boundary:** an unauthenticated `GET /api/runs` returns 401 `Authentication required`;
  `/api/auth/session` is the only anonymous API read. Sign-out invalidates the local session and
  clears the CSRF cookie, so the subsequent `/api/runs` is 401. The Keycloak SSO session is *not*
  ended by the application logout; signing in again may therefore complete without a password
  prompt until the Keycloak session expires. This is standard SSO behavior and is documented as a
  limitation, not hidden.
- **Benchmark runner:** k6 runs inside the backend container. Under `local-auth` it authenticates
  with a process-local random `InternalRunnerToken` scoped to anonymous `GET /api/products*`.
  The token is passed only to the child process, is not returned by any endpoint, and rotates on
  backend restart. It does not grant run orchestration, comparisons, or any write.
- **Alerting:** with the backend stopped, Prometheus moved `QueryForgeBackendUnavailable` through
  pending → firing; Alertmanager delivered `[FIRING:1]` to `operations@queryforge.local` in
  Mailpit. After the backend restarted, Alertmanager delivered `[RESOLVED]`.
- **Backup/restore:** `pg_dump -Fc` was restored into an isolated `qf_restore_check` database;
  `products` count and id range matched the live database, and both had 4 public tables. The
  scratch database and dump were removed afterwards.
- **Restart persistence:** backend restart (image rebuild) and `docker restart` of PostgreSQL both
  preserved run history and evidence; the real-stack suite passed afterwards. Hikari logged
  expected `Failed to validate connection ... connection has been closed` warnings and recovered.

## Known verification limits

- The production-shaped deployment (`docker-compose.production.yml`, TLS, external OIDC/SMTP) is
  configured but was not deployed against real infrastructure. It is outside this verdict.
- The local database contains **250 seeded products**, not 1,000,000. Headline latency and
  throughput numbers in the README are historical evidence from the 1M-row benchmark datasets;
  they are not re-measured on this 250-row local stack.
- The real-stack suite exercises one short QF-001 run (10 s, 5 req/s) plus a cancelled run. It is
  an integration acceptance, not a performance benchmark.
- Keycloak SSO survives application sign-out (see above).
- The internal runner token travels as a process argument and an HTTP header inside the Compose
  network. It is acceptable for a loopback-only local deployment; a production runner should use a
  dedicated service identity or a private, non-HTTP channel.
- jsdom prints `HTMLCanvasElement.getContext()` warnings because the `canvas` npm package is not
  installed. Tests pass; the warnings are noise, not failures.
- Testcontainers PostgreSQL coverage skips rather than fails when Docker is unavailable. A skipped
  run is reported as blocked, never as passed.
