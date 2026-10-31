# QueryForge — Deep Audit Findings (Continuous Living Document)

**Audit Date:** 2026-10-31  
**Auditor:** Senior Full-Stack Code Review  
**Scope:** Reproducibility, Portability, Performance, Security, UX, Maintainability, CI/CD  
**Goal:** 95%+ complete product — imperfections only, no "obvious" gaps

---

## REPRODUCIBILITY

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| REP-001 | **No deterministic seed for benchmark data generation** | High | `scripts/generate-data.py` uses `random` without fixed seed; `benchmark/seed/products_seed42.csv` implies seed 42 but not enforced in code | Add `--seed` parameter to generator; enforce in CI |
| REP-002 | **k6 scripts use `Math.random()` without seed** | High | `benchmark/k6/search.js:29`, `pagination.js:66`, `point.js:27` use `Math.random()` for page/ID selection; no seed support | Use k6's `--seed` flag or `__ENV.SEED` + custom RNG |
| REP-003 | **PostgreSQL `pg_stat_statements` not reset between runs** | Medium | Orchestrator doesn't call `pg_stat_statements_reset()`; cumulative stats pollute comparisons | Add reset in `ExperimentOrchestrator.setup()` |
| REP-004 | **No environment fingerprint in run manifest** | High | `RunManifest` lacks Java version, PG version, OS, commit hash, config hash | Extend `RunManifest.fingerprint` with full env capture |
| REP-005 | **Docker images not pinned to SHA in Compose** | Medium | `docker-compose.yml` uses tags (`postgres:17`, `keycloak:26.0.7`) not digests | Pin to `sha256:` digests for true reproducibility |
| REP-006 | **Maven `dependency:go-offline` not in CI** | Medium | CI may fetch deps at build time; no offline cache verification | Add `mvn dependency:go-offline` to CI cache step |
| REP-007 | **Data generator `scripts/generate-data.py` not deterministic** | High | Uses `random` module without fixed seed; `benchmark/seed/products_seed42.csv` implies seed 42 but not enforced | Add `--seed` parameter; enforce in CI |
| REP-008 | **k6 summary output path uses timestamp in filename** | Medium | `handleSummaryWith()` in `common.js` uses `__ENV.OUT` but fallback includes no run ID | Ensure `OUT` always includes run ID for traceability |
| REP-009 | **No `pg_stat_clear_snapshot()` between measurements** | Medium | Cumulative stats can skew subsequent runs | Reset stats before each k6 run |

### Reproducibility Score: 5/10

---

## PORTABILITY

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| POR-001 | **Hardcoded `127.0.0.1` in Keycloak redirect URIs** | High | `docker/keycloak/queryforge-realm.json` has `http://127.0.0.1:8080` — fails on LAN/WLAN | Use relative paths or configurable `QF_BASE_URL` |
| POR-002 | **Frontend `sseEvents()` uses `window.location.origin`** | Medium | `api.service.ts:244` — breaks if frontend served from different host than API | Accept configurable `apiBase` |
| POR-003 | **Nginx config proxies `/id/` to Keycloak with hardcoded upstream** | Medium | `frontend/nginx.conf` uses `keycloak:8080` — works in Compose only | Make upstream configurable via env |
| POR-004 | **No `docker-compose.override.yml.example` for local customization** | Low | Users must edit main Compose file | Provide override template |
| POR-005 | **Java 25 requirement not documented in README** | Medium | `pom.xml` has `<java.version>25</java.version>` but README says "Java 21+" | Sync documentation |
| POR-006 | **Windows line endings in repo (CRLF warnings on git diff)** | Low | Git shows `LF will be replaced by CRLF` for multiple files | Add `.gitattributes` with `* text=auto eol=lf` |
| POR-007 | **No ARM64/M1 Docker image variants** | Medium | All images `amd64` only; fails on Apple Silicon without emulation | Add multi-arch builds |
| POR-008 | **`BASE_URL` hardcoded in k6 scripts as `http://localhost:8080`** | High | `benchmark/k6/common.js:4` — breaks if API on different host/port | Make fully configurable via `__ENV.BASE_URL` (already supported) but document |
| POR-009 | **Nginx `keycloak:8080` upstream hardcoded** | Medium | `frontend/nginx.conf:79` — only works in Compose | Make upstream configurable via env var |
| POR-010 | **Frontend `proxy.conf.json` uses hardcoded `http://backend:8080`** | Medium | Dev server proxy only works in container | Support configurable target |
| POR-011 | **README says "Java 21+" but pom.xml requires Java 25** | Medium | `README.md:7` vs `pom.xml:25` | Sync documentation |
| POR-012 | **No `.env.example` for local configuration** | Low | Users must guess environment variables | Create `.env.example` with all `QF_*` vars |

### Portability Score: 4/10

---

## PERFORMANCE

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| PER-001 | **N+1 in run history: `getRuns()` loads 100 runs, each with separate artifact queries** | High | `RunHistoryController.getRuns()` → `RunRepository.findAll()` → artifacts lazy-loaded per run | Use `JOIN FETCH` or `@EntityGraph` for artifacts |
| PER-002 | **`ExperimentRunController.getRun()` doesn't use `@EntityGraph` for hints/artifacts** | Medium | `getRun(id)` + `getHints(id)` + `getArtifacts(id)` = 3 queries | Single query with entity graph |
| PER-003 | **SSE connection per run detail page — no connection pooling** | Medium | Each `/runs/:id` opens new `EventSource`; 10 tabs = 10 connections | Shared SSE service with ref-counting |
| PER-004 | **Frontend caches runs for 30s but `invalidateRuns()` clears all** | Low | `ApiService.cache` TTL 30s; any mutation invalidates entire cache | Granular invalidation (by run ID) |
| PER-005 | **k6 `search.js` uses `http.batch()` but no connection reuse across VUs** | Medium | Each VU creates new connections; no keep-alive tuning | Configure k6 `http.batch` with `tags` for connection pooling |
| PER-006 | **No database connection pool sizing for benchmark load** | High | `HikariCP` defaults (10 connections); k6 `concurrency.js` uses 50 VUs | Tune `spring.datasource.hikari.maximumPoolSize` |
| PER-007 | **Plan explorer loads entire plan tree recursively — no virtualization** | Medium | Large plans (1000+ nodes) freeze UI | Add virtual scrolling or pagination |
| PER-008 | **Frontend bundle size unknown — no `ng build --stats-json` analysis** | Low | No bundle budget in `angular.json` | Add budgets + analyze with `webpack-bundle-analyzer` |
| PER-009 | **`RunHistoryController.list()` loads ALL runs into memory before pagination** | High | `ExperimentRunService.findPage()` calls `findAll()` which loads all runs | Implement DB-level pagination with `Pageable` |
| PER-010 | **`ArtifactService.readPlan()` caches 64 plans in memory — no eviction policy** | Medium | `PLAN_CACHE_MAX = 64` with LRU but no TTL | Add TTL + size-based eviction |
| PER-011 | **k6 `concurrency.js` uses `constant-vus` executor — no warmup period** | Medium | Cold start affects first measurements | Add `startupTime` or pre-allocated VUs warmup |
| PER-012 | **No query plan analysis in CI — only runtime metrics** | Medium | EXPLAIN plans not captured/verified in CI | Add plan comparison in CI |
| PER-013 | **SSE reconnects on terminal state — `onerror` closes but browser may reopen** | Low | `RunDetailComponent.onerror` logic may not prevent reconnect | Ensure `EventSource` closed permanently on terminal |

### Performance Score: 3/10

---

## SECURITY

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| SEC-001 | **Keycloak client secret in plaintext in Spring properties** | Critical | `application-local-auth.properties:12` `spring.security.oauth2.client.registration.queryforge.client-secret=queryforge-local-secret` | Use Docker secrets / env substitution (`${QF_OIDC_CLIENT_SECRET_FILE}`) |
| SEC-011 | **Keycloak client secret in plaintext in realm JSON import** | Critical | `docker/keycloak/queryforge-realm.json:14` `"secret": "queryforge-local-secret"` | Use Docker secrets / env substitution at import time |
| SEC-002 | **Keycloak admin password in plaintext in Compose** | Critical | `docker-compose.yml:87` `KC_BOOTSTRAP_ADMIN_PASSWORD: ${QF_KEYCLOAK_ADMIN_PASSWORD:-admin-local-only}` | Require env var; no default |
| SEC-003 | **PostgreSQL password in Compose with weak default** | High | `docker-compose.yml:8` `POSTGRES_PASSWORD: ${QF_DB_PASSWORD:-queryforge}` | Require strong `QF_DB_PASSWORD` |
| SEC-004 | **No CSP nonce for inline scripts in nginx** | Medium | `frontend/nginx.conf` has CSP but inline scripts in Angular need nonce | Add `script-src 'self' 'nonce-...'` |
| SEC-005 | **SSE endpoint `/api/runs/{id}/events` has no authorization check** | High | `ExperimentRunController.java:66-84` — only checks run exists, no `@PreAuthorize` | Add `@PreAuthorize("hasRole('OPERATOR') or @runSecurity.canRead(#id)")` |
| SEC-006 | **Artifact download `/api/artifacts/{artifactId}` delegates to `ArtifactService.resolve()` — has path traversal protection but no run-scoped check** | Medium | `ArtifactController.java:26-36` → `ArtifactService.resolve()` (lines 136-162) rejects `..`, `/`, `\` but serves any artifact ID globally | Prefer run-scoped endpoint `/api/runs/{id}/artifacts/{artifactId}`; deprecate global artifact endpoint |
| SEC-007 | **Rate limit uses `X-Forwarded-For` but trusts header without proxy validation** | Medium | `RateLimitFilter.java:105-108` reads `X-Forwarded-For`; if proxy not configured to strip, client can spoof | Configure `server.forward-headers-strategy=framework` + trusted proxy config; validate header chain |
| SEC-008 | **No security headers on SSE responses** | Low | `EventSource` responses missing `X-Content-Type-Options`, `X-Frame-Options` | Add filter for SSE endpoints |
| SEC-009 | **Keycloak `directAccessGrantsEnabled: false` but `password` grant not tested** | Low | Realm has `directAccessGrantsEnabled: false` — good, but not verified in tests | Add test confirming password grant rejected |
| SEC-010 | **No dependency vulnerability scanning in CI** | Medium | CI runs CodeQL but no `mvn dependency-check` or `npm audit` | Add OWASP Dependency Check + npm audit |
| SEC-011 | **`LocalSecurityConfig` disables CSRF entirely (`csrf().disable()`)** | Critical | `LocalSecurityConfig.java:16` — default dev profile has NO CSRF protection | Keep CSRF enabled; only relax for specific test endpoints |
| SEC-013 | **`InternalRunnerFilter` grants `ROLE_OPERATOR` to benchmark runner** | High | `ProductionSecurityConfig.java:175-180` — runner gets full operator role | Create dedicated `ROLE_RUNNER` with minimal permissions |
| SEC-014 | **No rate limiting on authentication endpoints** | Medium | `/oauth2/**`, `/login/**` permitAll with no rate limit | Add rate limit to auth endpoints |
| SEC-015 | **CSP in `ProductionSecurityConfig` allows `'unsafe-inline'` for styles** | Medium | `script-src 'self'` but `style-src 'self' 'unsafe-inline'` | Use nonce/hash for inline styles |
| SEC-016 | **No Content-Type sniffing protection on artifact downloads** | Low | `ArtifactController` sets content-type but no `X-Content-Type-Options` | Add header filter |
| SEC-017 | **No HSTS header in nginx/production** | Medium | Production TLS deployment should enforce HSTS | Add `Strict-Transport-Security` header |
| SEC-018 | **`RunEventBus` broadcasts to all subscribers — no authorization** | Medium | `RunEventBus` emits to all SSE connections for a run ID | Verify subscriber has read access to run |

### Security Score: 2/10

---

## USER EXPERIENCE

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| UX-001 | **No registration / forgot password — blocked at login** | Critical | Keycloak realm has `registrationEnabled: false`, `resetPasswordAllowed: false` | Enable in realm + custom login theme |
| UX-002 | **No "rerun with same parameters" — must re-enter all params** | High | Run detail has "Test in lab" (changes OUT) but no true rerun | Add `POST /api/runs/{id}/rerun` |
| UX-003 | **Run history: no column sorting, no pagination UI** | High | `RunsComponent` has filters only; loads 100 max | Add `mat-sort` or native sort + pagination |
| UX-004 | **No bulk artifact download (ZIP)** | Medium | Must download each artifact individually | Add `GET /api/runs/{id}/artifacts?format=zip` |
| UX-005 | **Live log panel: no timestamps, levels, search, pause** | Medium | `RunDetailComponent.liveLogs` is raw string array | Add structured log entries + filter UI |
| UX-006 | **Comparison: no history, no save, no multi-run** | Medium | `CompareComponent` only A vs B, no persistence | Add `Comparison` entity + `/comparisons` page |
| UX-007 | **Plan explorer: no cost highlighting, no diff mode** | Medium | `PlanExplorerComponent` shows raw tree only | Add cost badges + side-by-side diff |
| UX-008 | **No keyboard shortcuts / command palette** | Low | Power users expect `g e`, `g r`, `g c`, `/` | Add global shortcut handler |
| UX-009 | **Empty states don't guide to action** | Low | "No runs recorded" — no link to `/experiments` | Add action links in `StateCardComponent` |
| UX-010 | **No loading skeletons on route transitions** | Low | Some pages show skeletons, others blank | Consistent skeleton cards everywhere |
| UX-011 | **Keycloak login page unbranded (stock Keycloak)** | Medium | Stock "login-pf" theme; no QueryForge branding | Custom Keycloak theme |
| UX-012 | **No in-app help / contextual docs** | Low | Users must leave app for documentation | Add help panel + tooltip system |
| UX-013 | **No "copy run ID" button** | Low | Run IDs are UUIDs; triple-click to copy | Add copy button with toast feedback |
| UX-014 | **Dark mode not synced to Keycloak login page** | Low | App has theme toggle; Keycloak login ignores it | Custom Keycloak theme with CSS vars |
| UX-015 | **Run history table: no column sorting (click headers)** | High | `RunsComponent` has filters only; no `mat-sort` or native sort | Add click-to-sort on all columns |
| UX-016 | **Run history: no pagination UI — loads 100 max, no "load more"** | High | `ExperimentRunService.findPage()` supports page/size but UI doesn't use it | Add pagination controls (prev/next, page size selector) |
| UX-017 | **Empty state "No runs recorded" doesn't link to `/experiments`** | Low | `StateCardComponent` message is static | Add action button/link in empty state |
| UX-018 | **No "copy run ID" button — UUIDs require triple-click** | Low | Run IDs are 36-char UUIDs | Add copy-to-clipboard with toast |
| UX-019 | **Live log panel: no timestamps, levels, search, pause** | Medium | `RunDetailComponent.liveLogs` is raw string array | Add structured log entries + filter UI |
| UX-020 | **Comparison: no history, no save, no multi-run** | Medium | `CompareComponent` only A vs B, no persistence | Add `Comparison` entity + `/comparisons` page |
| UX-021 | **Plan explorer: no cost highlighting, no diff mode** | Medium | `PlanExplorerComponent` shows raw tree only | Add cost badges + side-by-side diff |
| UX-022 | **No keyboard shortcuts / command palette** | Low | Power users expect `g e`, `g r`, `g c`, `/` | Add global shortcut handler |
| UX-023 | **No in-app help / contextual docs** | Low | Users must leave app for documentation | Add help panel + tooltip system |
| UX-024 | **Keycloak login page unbranded (stock Keycloak)** | Medium | Stock "login-pf" theme; no QueryForge branding | Custom Keycloak theme |
| UX-025 | **No "rerun with same parameters" — "Test in lab" changes OUT** | High | Run detail has "Test in lab" (changes OUT) but no true rerun | Add `POST /api/runs/{id}/rerun` |
| UX-026 | **No bulk artifact download (ZIP)** | Medium | Must download each artifact individually | Add `GET /api/runs/{id}/artifacts?format=zip` |
| UX-027 | **No loading skeletons on route transitions** | Low | Some pages show skeletons, others blank | Consistent skeleton cards everywhere |
| UX-028 | **Error states show generic messages — no "retry" or context** | Medium | "Run could not be loaded" — no retry button with context | Add contextual retry with error details |

### UX Score: 3/10

---

## MAINTAINABILITY

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| MAI-001 | **`ExperimentOrchestrator` 400+ lines — god class** | High | `ExperimentOrchestrator.java` handles scheduling, process exec, SSE, manifests, cleanup | Split: `RunScheduler`, `ProcessExecutor`, `RunEventPublisher`, `ManifestWriter` |
| MAI-002 | **`K6RunnerAdapter` mixes script generation, execution, parsing** | High | `K6RunnerAdapter.java` 200+ lines; generates JS, runs process, parses JSON | Separate: `K6ScriptGenerator`, `ProcessRunner`, `K6ResultParser` |
| MAI-003 | **Frontend `ApiService` does caching, dedup, HTTP, SSE — 4 concerns** | Medium | `api.service.ts` 247 lines; cache, inflight dedup, REST, SSE | Split: `ApiClient`, `CacheService`, `SseService` |
| MAI-004 | **No interface for `ExperimentRunner` — only `K6RunnerAdapter`** | Medium | `ExperimentRunner` interface exists but only one impl; hard to swap | Keep interface; document extension point |
| MAI-005 | **Magic strings for run states: `"QUEUED"`, `"RUNNING"`, `"SUCCEEDED"`** | Low | Scattered in Java + TypeScript; no shared enum | Create `RunState` enum in shared module or constants file |
| MAI-006 | **No architecture decision records (ADRs) for key choices** | Low | No `docs/adr/` directory | Add ADRs for: OIDC vs JWT, SSE vs WebSocket, k6 as runner, etc. |
| MAI-007 | **Test utilities scattered — no shared test fixtures** | Medium | Each test class creates own `ExperimentRun` builders | Add `TestFixtures` / `RunBuilder` utility |
| MAI-008 | **No strict TypeScript config — `strict: false` implied** | Medium | `frontend/tsconfig.json` not checked | Enable `strict: true`, `noImplicitAny: true` |
| MAI-009 | **No ESLint / Prettier in frontend CI** | Low | Only `ng test` + Playwright; no lint step | Add `npm run lint` to CI |
| MAI-010 | **`ExperimentOrchestrator` has inner class `LogTailer` (88 lines) — should be separate** | Medium | `ExperimentOrchestrator.java:646-734` | Extract `LogTailer` to own file |
| MAI-011 | **`K6RunnerAdapter.drain()` is static but tied to instance logic** | Medium | `K6RunnerAdapter.java:157-186` — static method with cap logic | Extract to `ProcessOutputDrainer` utility |
| MAI-012 | **`PlanParser` 269 lines — complex regex parsing, hard to maintain** | High | `PlanParser.java` — single class with 15+ regex patterns | Split into `PlanTokenizer`, `PlanTreeBuilder`, `PlanNodeFactory` |
| MAI-013 | **`ComparisonService` 431 lines — builds HTML/Markdown inline** | High | `ComparisonService.java:289-393` — string concatenation for reports | Use template engine (Thymeleaf/FreeMarker) or separate renderer |
| MAI-014 | **`HintService` rules hardcoded — no extensibility** | Medium | `HintService.java:35-91` — if/else chain | Rule engine with pluggable `HintRule` interface |
| MAI-015 | **No shared constants for run states between Java/TypeScript** | Low | `"QUEUED"`, `"RUNNING"`, `"SUCCEEDED"` duplicated | Generate TypeScript from Java enum or shared JSON |
| MAI-016 | **`ApiService` cache key strings constructed manually — error-prone** | Medium | `'runs'`, `'run:' + id` — no type safety | Use typed cache keys or constants |
| MAI-017 | **No architecture decision records (ADRs)** | Low | No `docs/adr/` directory | Add ADRs for: OIDC vs JWT, SSE vs WebSocket, k6 as runner, etc. |
| MAI-018 | **Test fixtures duplicated across test classes** | Medium | Each test class creates own `ExperimentRun` builders | Add `TestFixtures` / `RunBuilder` utility |
| MAI-019 | **No strict TypeScript config — `strict: false` implied** | Medium | `frontend/tsconfig.json` not checked | Enable `strict: true`, `noImplicitAny: true` |

### Maintainability Score: 4/10

---

## CI/CD

### Critical Findings

| ID | Finding | Severity | Evidence | Fix |
|----|---------|----------|----------|-----|
| CIC-001 | **CI runs on `ubuntu-latest` only — no Windows/macOS** | Medium | `.github/workflows/ci.yml` single OS | Add matrix: `ubuntu-latest`, `windows-latest`, `macos-latest` |
| CIC-002 | **No dependency caching for Maven/npm** | Medium | CI downloads deps every run | Add `actions/cache` for `~/.m2`, `~/.npm`, `node_modules` |
| CIC-003 | **Playwright tests run in CI but no artifact upload on failure** | Medium | No `actions/upload-artifact` for screenshots/traces | Upload `test-results/**` on failure |
| CIC-004 | **No `npm audit` / `mvn dependency-check` in CI** | High | Only CodeQL; no OWASP dependency check | Add both to CI |
| CIC-005 | **No performance regression gate** | Medium | Benchmark results not compared to baseline | Add `k6` threshold checks in CI |
| CIC-006 | **No Docker image build + push in CI** | Medium | Images built locally only | Add `docker buildx build --push` on tag |
| CIC-007 | **No release automation (changelog, tag, GitHub Release)** | Low | Manual `CHANGELOG.md` updates | Add `release-please` or `semantic-release` |
| CIC-008 | **CI doesn't test `local-auth` profile** | High | CI uses `default` profile; authenticated stack untested in CI | Add `QF_SPRING_PROFILE=local-auth` CI job |
| CIC-009 | **No PR size / complexity checks** | Low | Large PRs merge without review gates | Add `github-actions/pr-size-limit` |
| CIC-010 | **No accessibility audit in CI** | Medium | WCAG 2.2 AA not verified automatically | Add `axe-core` + Playwright a11y checks |
| CIC-011 | **No bundle size regression check in CI** | Medium | `angular.json` has budgets but CI doesn't enforce | Add `ng build --stats-json` analysis |

### CI/CD Score: 3/10

---

## SUMMARY SCORECARD

| Pillar | Score | Weight | Weighted |
|--------|-------|--------|----------|
| Reproducibility | 5/10 | 15% | 0.75 |
| Portability | 4/10 | 10% | 0.4 |
| Performance | 3/10 | 20% | 0.6 |
| Security | 2/10 | 20% | 0.4 |
| User Experience | 3/10 | 15% | 0.45 |
| Maintainability | 4/10 | 10% | 0.4 |
| CI/CD | 3/10 | 10% | 0.3 |
| **TOTAL** | | **100%** | **3.3/10** |

---

## PRIORITIZED ACTION PLAN (95%+ Target)

### Phase 1 — Security & Reproducibility (Week 1)
- [x] SEC-012: Re-enable CSRF in `LocalSecurityConfig` (dev profile)
- [x] SEC-013: Create dedicated `ROLE_RUNNER` for benchmark runner (minimal perms)
- [x] SEC-017, SEC-018: Remove weak defaults from Compose (Keycloak admin, PG password)
- [x] SEC-001, SEC-011: Remove secrets from repo; require env vars / Docker secrets
- [x] SEC-005: Add authorization to SSE endpoint (`@PreAuthorize`)
- [x] SEC-006: Prefer run-scoped artifact endpoint; deprecate global (`@Deprecated` + Deprecation header)
- [x] SEC-010: Add OWASP Dependency Check + npm audit to CI
- [x] REP-001, REP-007: Enforce deterministic seeds in data gen + k6
- [x] REP-004: Full environment fingerprint in run manifest
- [x] REP-005: Pin Docker images to SHA digests

### Phase 2 — Performance & Portability (Week 2)
- [x] PER-009: Fix N+1 in run history — DB-level pagination with `Pageable`
- [x] PER-001, PER-002: N+1 N/A (runs are file-based; `findByTemplate` + `listByTemplate` now paginated, dead `findAll()` removed)
- [x] PER-006: Tune HikariCP pool for benchmark concurrency
- [x] POR-001, POR-008: Make base URLs fully configurable (`__QF_API_BASE__`)
- [x] POR-006: Add `.gitattributes` for LF normalization
- [x] POR-007: Add multi-arch Docker build job to CI (buildx, linux/amd64+arm64)
- [x] POR-012: Create `.env.example` with all `QF_*` vars

### Phase 3 — UX Polish (Week 3)
- [x] UX-001: Enable Keycloak registration + forgot password + custom theme
- [x] UX-025: Add "Rerun with same params" endpoint + button
- [x] UX-015: Add column sorting to run history table
- [x] UX-016: Add pagination UI to run history
- [x] UX-026: Add bulk artifact download (ZIP)
- [x] UX-019: Structured live logs with timestamps/levels/search
- [x] UX-020: Comparison history + multi-run support
- [x] UX-021: Plan cost highlighting + diff mode
- [x] UX-018: Add "copy run ID" button with toast feedback
- [x] UX-017: Link empty state to `/experiments`

### Phase 4 — Maintainability & CI/CD (Week 4)
- [ ] MAI-001: Split `ExperimentOrchestrator` into 4 focused classes
- [ ] MAI-002: Split `K6RunnerAdapter` into generator/runner/parser
- [ ] MAI-003: Split `ApiService` into client/cache/SSE services
- [ ] MAI-012: Split `PlanParser` into tokenizer/tree builder/node factory
- [ ] MAI-013: Extract comparison report rendering to template engine
- [ ] MAI-020: Enable strict TypeScript config
- [ ] CIC-010: Multi-OS CI matrix
- [ ] CIC-011: Dependency caching
- [ ] CIC-012: Playwright artifact upload on failure
- [ ] CIC-017: CI job for `local-auth` profile
- [ ] CIC-018: Accessibility audit in CI (axe-core)

### Phase 5 — Portfolio Polish (Week 5)
- [ ] MAI-017: ADRs for all key architectural decisions
- [ ] PER-008: Bundle size budgets + analysis
- [ ] CIC-016: Release automation
- [ ] Documentation sync (README, operations, API)
- [ ] Accessibility audit (WCAG 2.2 AA)
- [ ] POR-011: Sync Java version in README (25 not 21+)

---

## NEXT STEPS

This document will be continuously updated as we:
1. Verify each finding with code references
2. Implement fixes
3. Re-audit after changes
4. Track progress toward 95%+ completeness

**Current State:** Deep audit complete. Ready for Phase 1 implementation.