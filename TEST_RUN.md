# Test Run Guide

## Overview

QueryForge has three test layers:

1. **Unit tests** — pure Java, no external services.
2. **Functional tests** — Spring Boot test slice; uses H2 in-memory by default so Docker/Testcontainers is not required.
3. **E2E smoke tests** — Playwright against the Angular frontend (`http://localhost:4200`).

## Quick run (everything)

```bash
# Backend unit + functional tests
./mvnw -B -ntp test

# Frontend E2E (requires backend on :8080 and frontend on :4200)
cd frontend
npx playwright test
```

## Known Docker / Testcontainers Windows issues

- **Named-pipe access**: Docker Desktop on Windows exposes its daemon over named pipes (`//./pipe/docker_engine`). Some JDK versions/network configs cannot reach them from the Java client, causing Testcontainers to fail to pull `postgres:17`.
- **Workaround**: the functional tests are configured to use H2 in-memory when no explicit `QF_DB_URL` is set, so `mvn test` works without Docker.
- If you *do* want to run against the compose database, set:
  ```bash
  QF_DB_URL=jdbc:postgresql://localhost:5433/queryforge ./mvnw -B -ntp test
  ```
  Ensure `docker compose up -d db` is running and the `queryforge` database exists.

## Playwright E2E details

The E2E suite lives in `frontend/e2e/` and covers:

- `smoke.spec.ts` — homepage, catalog, run history, run detail, compare, artifact download, keyboard navigation.
- `screenshots.spec.ts` — captures PNGs for README.

### Prerequisites

- Backend running on `http://localhost:8080` (the Angular dev-server proxy forwards `/api` to it).
- Frontend dev server running on `http://localhost:4200` (`cd frontend && ng serve`).

### Execution

```bash
cd frontend
npx playwright test                    # all E2E
npx playwright test e2e/smoke.spec.ts  # smoke only
npx playwright test e2e/screenshots.spec.ts  # screenshots only
```

Artifacts (HTML report, traces) land in `frontend/test-results/`.

## Manual verification steps

1. **API health**
   ```bash
   curl http://localhost:8080/api/lab/readiness
   ```
   Expect `api.status = UP`, `database.status = UP`, `k6.status = UP`.

2. **Product endpoints**
   ```bash
   curl "http://localhost:8080/api/products/1"
   curl "http://localhost:8080/api/products?category=1&status=ACTIVE&page=0&size=50"
   curl "http://localhost:8080/api/products/keyset?category=1&size=50"
   ```

3. **Frontend pages**
   - Open `http://localhost:4200` → Overview shows environment card and latest run.
   - Open `http://localhost:4200/experiments` → Catalog shows QF-000 through QF-004 cards.
   - Open `http://localhost:4200/runs` → Run history shows existing runs in a table.
   - Open `http://localhost:4200/runs/qf001-baseline-search-1M-r10` → Run detail shows metrics, hints, and artifacts.
   - Open `http://localhost:4200/compare` → Select two runs, click Compare, verify verdict + rule cards appear.

4. **Artifact download**
   - From run detail, click any artifact link (e.g. `k6-summary`).
   - Verify the browser opens/downloads the JSON or text file.

5. **Accessibility spot-check (NFR-006)**
   - Tab through the catalog page: focus should move between brand, nav links, and launch buttons.
   - Verify every `<img>` has an `alt` attribute (current icons are inline SVGs without decorative `aria-hidden`).
   - Verify form controls in Compare have associated `<label>` elements.

## Test results summary

| Layer | Tests | Result |
| --- | --- | --- |
| Backend unit + functional | 72 | PASS |
| Playwright smoke | 7 | PASS |
| Playwright screenshots | 4 | PASS |
