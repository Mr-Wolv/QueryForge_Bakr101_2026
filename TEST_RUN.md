# Test Run Guide

## Overview

QueryForge has five test layers:

1. **Unit tests** — pure Java, no external services.
2. **Functional tests** — Spring Boot test slice; uses H2 in-memory by default so Docker/Testcontainers is not required.
3. **PostgreSQL integration test** — `PostgresIntegrationSmokeTest` starts a real `postgres:17` container
   with Testcontainers, applies `db/migrations/V1__create_products.sql`, and runs a filtered-search query.
   It is annotated `@Testcontainers(disabledWithoutDocker = true)`, so it skips (does not fail) when no
   Docker daemon is reachable.
4. **E2E smoke tests** — Playwright against the Angular frontend served from this checkout; the
   first-session test suite stubs API responses and runs without a backend.
5. **Frontend build** — production Angular compilation via `npm run build`.

## Quick run (everything)

```bash
# Backend unit + functional + integration tests
./mvnw -B -ntp test

# Frontend unit tests
cd frontend
npm test

# Frontend production build
npm run build

# Playwright fixture-backed suite (stubbed API)
npm run e2e:ci
npx playwright test
```

## Known Docker / Testcontainers Windows issues

- **Engine API version**: Docker Engine 25.0 raised its minimum API version to 1.44 and Engine 29 answers
  anything older with HTTP 400, while the docker-java client bundled with Testcontainers still defaults to
  1.32. On such daemons Testcontainers reports `Could not find a valid Docker environment` and the
  Postgres test skips. `pom.xml` passes `api.version=1.44` to Surefire so the test runs without any extra
  flag; check `docker version` before blaming the test.
- **Named-pipe access**: Docker Desktop on Windows exposes its daemon over named pipes (`//./pipe/docker_engine`). Some JDK versions/network configs cannot reach them from the Java client, causing Testcontainers to fail to pull `postgres:17`.
- **Workaround**: the functional tests are configured to use H2 in-memory when no explicit `QF_DB_URL` is set, so `mvn test` works without Docker.
- If you *do* want to run against the compose database, set:
  ```bash
  QF_DB_URL=jdbc:postgresql://localhost:5433/queryforge ./mvnw -B -ntp test
  ```
  Ensure `docker compose up -d db` is running and the `queryforge` database exists.

## Playwright E2E details

The E2E suite lives in `frontend/e2e/` and covers:

- `first-session.spec.ts` — empty-state onboarding, home navigation, and parameterized run creation/start against deterministic API fixtures.
- `navigation.spec.ts` and `smoke.spec.ts` — routing, theme persistence, run history, explain-plan explorer, compare, artifact download, and keyboard navigation against a running backend with data.
- `screenshots.spec.ts` — optional local captures; screenshots are generated, not committed.

GitHub Actions runs the backend tests/package, frontend production build, and the fixture-backed
first-session browser suite. The remaining browser smoke tests require a backend and seeded runs.

### Prerequisites

- Backend running on `http://localhost:8080` (the Angular dev-server proxy forwards `/api` to it).
- Playwright starts the frontend from this checkout and refuses to reuse another process. It uses
  port `4200` by default; set `$env:PLAYWRIGHT_PORT = '4201'` if that port is occupied.

### Execution

```bash
cd frontend
$env:PLAYWRIGHT_PORT = '4201'
npm run e2e:ci
npx playwright test
npx playwright test e2e/smoke.spec.ts
npx playwright test e2e/screenshots.spec.ts
```

Artifacts (HTML report, traces) land in `frontend/test-results/`.

## Manual verification steps

1. **API health**
   ```bash
   curl http://localhost:8080/api/lab/readiness
   ```
   Expect `api.status = UP` and `database.status = UP`. `k6.status` is `UP` when k6 is available on `PATH` or configured with `K6_PATH`/`K6_BIN`; otherwise it is `UNKNOWN`.

2. **Product endpoints**
   ```bash
   curl "http://localhost:8080/api/products/1"
   curl "http://localhost:8080/api/products?category=1&status=ACTIVE&page=0&size=50"
   curl "http://localhost:8080/api/products/keyset?category=1&size=50"
   ```

3. **Frontend pages**
   - Open `http://127.0.0.1:4201` → Overview shows environment card and latest run.
   - Open `http://127.0.0.1:4201/experiments` → Catalog shows QF-000 through QF-004 cards.
   - Open `http://127.0.0.1:4201/runs` → Run history shows existing runs in a table.
   - Open `http://127.0.0.1:4201/runs/qf001-baseline-search-1M-r10` → Run detail shows metrics, hints, and artifacts.
   - Click **View plan** on the `explain-plan` artifact → the plan route renders the node tree with a Raw toggle.
   - Open `http://127.0.0.1:4201/compare` → Select two runs, click Compare, verify verdict + rule cards appear, then verify the grouped metric-delta table (baseline / candidate / change, colour-coded improvement vs regression).
   - Request an unknown path (e.g. `curl -i http://localhost:8080/api/nope`) → RFC 7807 body with `status: 404`, not 500.

4. **Artifact download**
   - From run detail, click any artifact link (e.g. `k6-summary`).
   - Verify the browser opens/downloads the JSON or text file.

5. **Accessibility spot-check (NFR-006)**
   - Tab through the catalog page: focus should move between brand, nav links, and launch buttons.
   - Verify every `<img>` has an `alt` attribute (current icons are inline SVGs without decorative `aria-hidden`).
   - Verify form controls in Compare have associated `<label>` elements.
