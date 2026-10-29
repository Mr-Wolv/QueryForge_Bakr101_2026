# 10 — Implementation Manifest

## 10.1 New Files to Create

| File | Lines | Purpose | Phase | Verification |
|------|-------|---------|-------|--------------|
| `frontend/src/styles/tokens.css` | ~120 | Single source of truth for colors, type, spacing, radii, motion. | 1 | `grep -c 'var(--' frontend/src/**/*.css` shows all components use tokens; no hard-coded values. |
| `frontend/src/app/core/api/error.model.ts` | 10 | Typed `ProblemDetail` interface. | 1 | Compiles; used by interceptor. |
| `frontend/src/app/core/api/api.interceptor.ts` | 30 | Injects `X-Request-Id`, maps RFC 7807 errors. | 1 | 400 response renders typed error in UI. |
| `frontend/src/app/core/theme/theme.service.ts` | 40 | Toggle dark/light mode, persist to `localStorage`, apply `data-theme`. | 1 | Toggle persists across reload. |
| `frontend/src/app/components/skeleton-card/skeleton-card.component.ts` | 60 | Shimmer skeleton loader with `loading`/`error` variants. | 1 | Replaces all `StateCardComponent` loading states. |
| `frontend/src/app/components/latency-chart/latency-chart.component.ts` | 80 | `ng2-charts` wrapper with percentile datasets, axes, tooltips. | 1 | Renders p50/p95/p99 with units. |
| `src/main/java/com/bakr/queryforge/config/ProblemDetailConfig.java` | 40 | RFC 7807 error attributes bean. | 1 | `/api/nonexistent` returns `ProblemDetail` JSON. |
| `src/main/java/com/bakr/queryforge/config/CorsConfig.java` | 20 | CORS registry for localhost + nginx origins. | 1 | Preflight `OPTIONS` returns 200 from nginx-frontend. |
| `src/main/java/com/bakr/queryforge/config/OpenApiConfig.java` | 30 | `springdoc-openapi` configuration. | 1 | `/v3/api-docs` returns JSON; `/swagger-ui.html` renders. |
| `src/main/java/com/bakr/queryforge/config/ActuatorConfig.java` | 20 | Expose health, metrics, prometheus; secure to localhost. | 2 | `curl localhost:8080/actuator/health` returns UP. |
| `src/main/resources/application.properties` additions | 12 | Actuator endpoints, Prometheus, OpenAPI path. | 1 | Properties file parses on startup. |
| `docs/renovation/README.md` | 20 | Plan index. | 0 | Document exists and links to all sections. |
| `docs/renovation/01-vision-and-boundaries.md` | ~80 | Product thesis, users, scope, success criteria. | 0 | Reviewed and accepted. |
| `docs/renovation/02-requirements-matrix.md` | ~120 | Full FR/NFR matrix with IDs, priorities, acceptance. | 0 | Every deliverable has a testable acceptance row. |
| `docs/renovation/03-architecture.md` | ~150 | Package map, runner boundary, artifact storage, API outline. | 0 | Architecture reviewed; no circular dependencies. |
| `docs/renovation/04-frontend-design-system.md` | ~200 | Token values, component specs, responsive rules, accessibility. | 1 | Components match spec pixel-to-pixel. |
| `docs/renovation/05-api-contract.md` | ~120 | Endpoint contracts, schemas, error envelope examples. | 1 | Generated OpenAPI matches hand-written examples. |
| `docs/renovation/06-test-strategy.md` | ~150 | Pyramid, unit/integration/E2E targets, regression list. | 0 | CI runs all suites; coverage gate enforced. |
| `docs/renovation/07-phases-and-gates.md` | ~180 | Phase plans, tasks, exit criteria, gated reviews. | 0 | Each phase has a checklist and review score ≥ 9.5/10. |
| `docs/renovation/08-definition-of-done.md` | ~80 | Shippable checklist + phase exit checklist. | 0 | Used as final gate before merge. |
| `docs/renovation/09-risk-register.md` | ~40 | Risks, likelihood, impact, mitigations, contingencies. | 0 | Reviewed at each phase exit. |

## 10.2 Existing Files to Modify

| File | Changes | Phase | Verification |
|------|---------|-------|--------------|
| `pom.xml` | Add `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-validation`. | 1 | `mvn dependency:tree` shows new deps; build passes. |
| `frontend/package.json` | Add `chart.js`, `ng2-charts` to dependencies. | 1 | `npm install` succeeds; bundle size within budget. |
| `frontend/angular.json` | Add `src/styles/tokens.css` to global styles array. | 1 | `ng build` includes tokens. |
| `frontend/src/app/app.css` | Replace ad-hoc vars with token references; remove fallbacks. | 1 | No hard-coded colors remain in `app.css`. |
| `frontend/src/app/app.html` | Add skip link, `id="main"` on `<main>`. | 1 | Tab navigates to skip link first. |
| `frontend/src/app/services/api.service.ts` | Add `X-Request-Id` interceptor usage (implicit via interceptor); type error responses. | 1 | TypeScript compiles without `any` in error paths. |
| `frontend/src/app/components/*/*.component.css` | Migrate all component CSS to token vars. | 1 | `grep -c 'var(--'` increases across components. |
| `src/main/java/com/bakr/queryforge/controller/ApiExceptionHandler.java` | Refactor to delegate to `ProblemDetail` attributes; add field-level validation error mapping. | 1 | Existing tests pass; new ProblemDetail schema verified. |
| `frontend/src/app/app.config.ts` | Add `provideHttpClient(withInterceptors([...]))`. | 1 | App boots; interceptor fires on every request. |
| `src/main/resources/application.properties` | Add actuator, prometheus, openapi, CORS-related properties. | 1 | `mvn spring-boot:run` exposes endpoints. |

## 10.3 Dependency Additions

### Backend (pom.xml)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Frontend (package.json)
```json
{
  "dependencies": {
    "chart.js": "^4.4.7",
    "ng2-charts": "^7.0.0"
  }
}
```

## 10.4 Acceptance Test Checklist

Run these commands at phase exit to verify state:

```bash
# Phase 1 exit
mvn test
cd frontend && npm run build
cd frontend && npx playwright test
cd frontend && npx playwright test --project=chromium --grep "a11y"
grep -R "hard-coded" frontend/src || echo "No hard-coded values found"

# Phase 2 exit
curl -s localhost:8080/actuator/health | jq .
curl -s localhost:8080/v3/api-docs | jq '.paths | keys | length'
curl -s localhost:8080/api/lab/readiness | jq '.database.status'

# Phase 3 exit
grep -R "QF-00[0-4]" frontend/src/app/components/experiments/
curl -s -X POST localhost:8080/api/runs -H 'Content-Type: application/json' -d '{"templateId":"QF-001","parameters":{"rate":"10","duration":"60s"}}' | jq .

# Phase 4 (gated)
grep -R "hypothesis" frontend/src/app/components/compare/ || echo "No hints yet"
```
