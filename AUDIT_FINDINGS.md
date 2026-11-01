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
| SEC-001 | **Keycloak client secret in plaintext in Spring properties** | Critical | `application-local-auth.properties:12` `spring.security.oauth2.client.registration.queryforge.client-secret=[redacted local fixture literal]` | Use Docker secrets / env substitution (`${QF_OIDC_CLIENT_SECRET_FILE}`) |
| SEC-011 | **Keycloak client secret in plaintext in realm JSON import** | Critical | `docker/keycloak/queryforge-realm.json:14` `"secret": "[redacted local fixture literal]"` | Use Docker secrets / env substitution at import time |
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
- [x] MAI-001: Split `ExperimentOrchestrator` into 4 focused classes
- [x] MAI-002: Split `K6RunnerAdapter` into generator/runner/parser
- [x] MAI-003: Split `ApiService` into client/cache/SSE services
- [x] MAI-012: Split `PlanParser` into tokenizer/tree builder/node factory
- [x] MAI-013: Extract comparison report rendering to template engine
- [x] MAI-020: Enable strict TypeScript config
- [x] CIC-010: Multi-OS CI matrix
- [x] CIC-011: Dependency caching
- [x] CIC-012: Playwright artifact upload on failure
- [x] CIC-017: CI job for `local-auth` profile
- [x] CIC-018: Accessibility audit in CI (axe-core)

### Phase 5 — Portfolio Polish (Week 5)
- [x] MAI-017: ADRs for all key architectural decisions
- [x] PER-008: Bundle size budgets + analysis
- [x] CIC-016: Release automation
- [x] Documentation sync (README, operations, API)
- [x] DB-backed runs store: migrate file-based ExperimentRun to JPA entity for true DB-level pagination/sorting/filtering, transactional writes, and concurrency safety (PER-009 original intent; currently custom in-memory PageResponse)
- [x] Accessibility audit (WCAG 2.2 AA) — automated axe-core in CI (CIC-018); inputs reviewed for labels; no critical violations in automated scan
- [x] POR-011: Sync Java version in README (25 not 21+)

### Phase 6 — Quality Assurance & Positive Metrics (Week 6)
- [ ] Wiring & dead-code audit (TOP PRIORITY, CODEBASE-WIDE, PRODUCTION + LOCAL DEV): low-risk dead-code / technical-debt / unwired guardrail — every class, component, service, bean, migration, and frontend service across the entire codebase (production AND local development paths) should have a concrete, exercised use; minimize orphaned/dead code; remove or wire anything unused where low-risk; precise, pragmatic code-quality evaluation (perfect zero is unrealistic at this scale)
- [ ] Backend ≥95% line coverage (JaCoCo) — gap-fix: `ExperimentOrchestrator.LogTailer`, `CompareRequest`, `ExperimentRunRequest`, `PlanParser.Frame`, `ComparisonController`, `ArtifactController`, `RunEventBus`, `RunHistoryController`, `HintController`
- [ ] Frontend ≥95% line coverage (Istanbul via `ng test --coverage`) — gap-fix: `skeleton-card` 11%, `toast` 33%, `runs` 42%, `latency-chart` 42%, `run-detail` 62%
- [ ] Mutation testing: PIT (Java) + Stryker (JS/TS) — prove tests actually kill seeded bugs
- [ ] Contract testing: Pact / Spring Cloud Contract — verify API contracts between backend & frontend
- [ ] SLOs/SLIs: error budget, latency, availability — production reliability targets
- [ ] Observability: structured logging, correlation IDs, tracing — detect/diagnose production failures
- [ ] Security findings: zero critical SAST/DAST/vuln results (OWASP Dependency Check, npm audit)
- [ ] Feature coverage mapping: traceability matrix — every audit finding + requirement has ≥1 test
- [ ] E2E coverage (bonus): instrumented backend + frontend, run Playwright against real stack, merge reports (cross-service attribution noted as messy — treat as bonus)

### Phase 7 — Production Readiness & Live Validation (Week 7)
- [ ] Performance metrics: memory/disk/CPU profiling and budgets (heap, RSS, disk usage, GC) under benchmark load
- [ ] Docs alignment with codebase: README, operations, API docs verified against actual code and endpoints
- [ ] GitHub clean public repo readiness: no secrets, LICENSE, CONTRIBUTING, CODE_OF_CONDUCT, issue/PR templates, clean history
- [ ] Brute live-stack application testing: end-to-end against real running stack (backend + frontend + Keycloak + Postgres), Playwright + k6, cross-service attribution; tests must exercise EVERY possible action in the live system (not just documented behavior) to surface defects

## Phase 1–5 Verification Audit (2026-11-01)

### Scope and method

- **Source snapshot:** local Git `main` commit `3153b5a9760c788b1b71f4faa618f96695d9da08` (47 commits ahead of `origin/main` when checked). The isolated worktree was detached at this exact commit for inspection. The primary checkout remained clean and read-only; no code, configuration, deployment, or commit changes were made.
- **Document baseline:** the untracked source file `D:\QueryForge_Bakr101_2026\audit_findings.md` was read without changing it. The pre-existing isolated-worktree audit document matched it after newline normalization. Because the Windows worktree is case-insensitive, the lowercase destination resolves to its existing tracked `AUDIT_FINDINGS.md`; a separate lowercase root file cannot coexist there. This section is appended only; the original findings and checked boxes above are preserved as historical claims, not endorsed by this adjudication. The primary local source file was not changed.
- **Evidence types:** statuses below distinguish source/config inspection from executed checks. A configured job is not represented as a successful run. Live-stack, production, multi-OS, OWASP Dependency Check, actual multi-architecture image builds, and k6 checks were not run.
- **Status meanings:** **Verified** = the stated source/configuration claim is present; **Partial** = some elements exist but the complete claim is not met; **Failed** = inspected implementation contradicts the claim; **Not verified** = insufficient evidence to adjudicate; **Blocked** = the relevant check could not run.
- File line numbers are 1-based at the stated source commit.

### Executed validation record

These commands were run in the isolated worktree at the exact source SHA above during the preceding validation pass in project session `0a94d5ae-58ba-4624-8070-e5d199c93581`. They were not rerun for this documentation-only append.

| Command | Result |
|---|---|
| `mvn -B -ntp test` | **Passed:** 197 tests; 0 failures, errors, or skips. |
| `mvn -B -ntp test '-Dspring.profiles.active=local-auth'` | **Failed:** 197 tests; 13 failures, 0 errors. Ten failures were in `ProductApiFunctionalTest`; three were in `FilterRegressionTest`. |
| `npm test` (from `frontend`) | **Passed:** 41 tests across 6 test files. |
| `npm run build` (from `frontend`) | **Failed:** Angular TS2349 at `frontend/src/app/core/toast/toast.component.ts:11`; the template invokes `toasts()` although it is an `Observable<Toast[]>`. |
| `npm ci` (from `frontend`) | **Failed:** `package.json` requires `@axe-core/playwright` and `axe-core`, but the lockfile lacks the required entries (`@axe-core/playwright@4.14.0`, `axe-core@4.14.0`). |
| `npm audit --audit-level=high` (after installing without changing package manifests/lockfile) | **Passed:** 0 reported vulnerabilities. This does not establish that the normal locked CI install succeeds. |
| `$env:PLAYWRIGHT_PORT='43129'; npx playwright test e2e/a11y.spec.ts` (from `frontend`) | **Blocked:** Playwright's local server failed to compile on the same TS2349 error and timed out after 120 seconds; no axe scan completed. |
| `docker compose config --quiet` | **Passed:** config-only validation with disposable validation values; no service was started. |
| `docker compose -f docker-compose.production.yml config --quiet` | **Passed:** config-only validation with temporary dummy secret files and validation values; no service was started. |

No live stack, real OIDC provider, production service, or k6 workload was exercised. The OWASP Dependency Check command is present in CI but was not run locally; its configured invocation requires the CI NVD API key. The multi-OS and multi-architecture jobs were inspected, not executed on their other target platforms.

### Item-by-item adjudication

#### Phase 1 — Security and reproducibility

| Exact checked claim | Status | Source evidence and adjudication |
|---|---|---|
| SEC-012: Re-enable CSRF in `LocalSecurityConfig` (dev profile) | **Verified** | `src/main/java/com/bakr/queryforge/config/LocalSecurityConfig.java:17-22` configures cookie CSRF protection and exempts only actuator/session paths. `LocalAuthenticatedSecurityConfigTest` and `ProductionSecurityConfigTest` were included in the passing default test run (2 and 9 tests respectively); the separate local-auth profile run as a whole failed as recorded above. |
| SEC-013: Create dedicated `ROLE_RUNNER` for benchmark runner (minimal perms) | **Verified** | `src/main/java/com/bakr/queryforge/config/ProductionSecurityConfig.java:62,157-181` limits runner authentication to anonymous GET product reads and grants `ROLE_RUNNER`, not operator. |
| SEC-017, SEC-018: Remove weak defaults from Compose (Keycloak admin, PG password) | **Partial** | `docker-compose.yml:8,88-89`: PostgreSQL password has no weak fallback, but Keycloak admin username still defaults to `admin`. `.env.example:5,17-18` uses explicit change-me placeholders and an `admin` username; README documents local-only fixture credentials at `README.md:94-102`. Production password inputs are Docker secrets (`docker-compose.production.yml:5-8,257-265`). Both Compose configurations passed config-only validation; no service was started. |
| SEC-001, SEC-011: Remove secrets from repo; require env vars / Docker secrets | **Partial** | `git ls-files` at the exact local-main commit confirms `cookies.txt` and `secrets/qf_oidc_client_secret.txt` are tracked. The latter's trimmed content was safely compared, without emitting it, and matches the documented dummy local Keycloak fixture literal `[redacted local fixture literal]`; the realm also declares that fixture value at `docker/keycloak/queryforge-realm.json:25`. The cookie jar contains one `XSRF-TOKEN` row for `127.0.0.1` with session expiry; no cookie value was copied or displayed, and validity cannot be inferred. No production credential was identified from this limited check, but the tracked local fixture/token material still contradicts a strict remove-secrets-from-repository claim. Production Compose uses secret files (`docker-compose.production.yml:5-8,257-265`); local Compose still mounts a client-secret file (`docker-compose.yml:49,190-191`). Treat any unrecognized/non-dummy value as potentially active and rotate it; the values assessed here were the local fixture and loopback XSRF token, not a verified production credential. |
| SEC-005: Add authorization to SSE endpoint (`@PreAuthorize`) | **Verified** | `src/main/java/com/bakr/queryforge/experiment/ExperimentRunController.java:75-76` protects the run event endpoint with `hasRole('OPERATOR')`; the production security chain also requires authentication for other `/api/**` requests (`ProductionSecurityConfig.java:68`). |
| SEC-006: Prefer run-scoped artifact endpoint; deprecate global (`@Deprecated` + Deprecation header) | **Verified** | Run-scoped retrieval is at `src/main/java/com/bakr/queryforge/experiment/ExperimentRunController.java:142-151`; the global controller is deprecated and returns deprecation/successor headers at `src/main/java/com/bakr/queryforge/experiment/ArtifactController.java:18,27-38,50-62`. Controller/security behavior was not independently exercised against a live stack. |
| SEC-010: Add OWASP Dependency Check + npm audit to CI | **Partial** | `.github/workflows/ci.yml:53-54,77` configures both. The locked frontend install failed due the axe package lock mismatch, so the CI audit path is not currently reproducible with `npm ci`. Local audit after a non-lock-mutating install reported 0 vulnerabilities. OWASP scanning was not executed locally. |
| REP-001, REP-007: Enforce deterministic seeds in data gen + k6 | **Verified** | `scripts/generate-data.py:17,23-25,45-46` defaults to seed 42 and uses per-row seeded RNG; `benchmark/k6/common.js:6-9,37-39` defaults seeded RNG to 42, consumed by `search.js:29`, `pagination.js:66`, and `point.js:27`. No k6 workload was run. |
| REP-004: Full environment fingerprint in run manifest | **Partial** | `src/main/java/com/bakr/queryforge/experiment/EnvironmentFingerprintService.java:79-114,132-148` records app commit, Java/vendor, OS/architecture, database/version, row count/indexes, CPU/memory, and capture time. It does not record the k6 runner version or a complete configuration fingerprint; “full” is therefore not established. |
| REP-005: Pin Docker images to SHA digests | **Partial** | Local Compose images are digest-pinned (e.g. `docker-compose.yml:4,85,120,133,144,160,175`); production Compose still uses mutable tags (`docker-compose.production.yml:3,112,140,165`). |

#### Phase 2 — Performance and portability

| Exact checked claim | Status | Source evidence and adjudication |
|---|---|---|
| PER-009: Fix N+1 in run history — DB-level pagination with `Pageable` | **Partial** | `src/main/java/com/bakr/queryforge/experiment/RunPersistenceService.java:31-34` uses `PageRequest` for unfiltered history; filtered history loads all matching rows at `:36-40` before slicing. No entity artifact relationship is present in `ExperimentRunEntity.java:15-50`, so the stated N+1 is not evident in that entity, but complete DB pagination is not implemented. |
| PER-001, PER-002: N+1 N/A (runs are file-based; `findByTemplate` + `listByTemplate` now paginated, dead `findAll()` removed) | **Partial** | The current run entity has scalar columns rather than artifact relationships (`src/main/java/com/bakr/queryforge/entity/ExperimentRunEntity.java:15-50`), so the specific artifact N+1 claim is not shown. However `ExperimentRunRepository.java:11-12` returns full lists for state/template queries; `RunPersistenceService.java:36-40,51` and `ExperimentRunService.java:61-69` paginate after loading. Repository `findAll(Pageable)` remains intentionally used for unfiltered paging (`RunPersistenceService.java:31-34`). |
| PER-006: Tune HikariCP pool for benchmark concurrency | **Verified** | `src/main/resources/application.properties:7-12` sets maximum/minimum pool sizes and connection/idle/lifetime timeouts. |
| POR-001, POR-008: Make base URLs fully configurable (`__QF_API_BASE__`) | **Partial** | Browser API and SSE clients accept `window.__QF_API_BASE__` (`frontend/src/app/services/api-client.service.ts:10`; `frontend/src/app/services/sse.service.ts:6`). The runner still hardcodes `BASE_URL=http://127.0.0.1:8080` (`src/main/java/com/bakr/queryforge/experiment/runner/K6RunnerAdapter.java:68`), and imported Keycloak redirect URIs are local literals (`docker/keycloak/queryforge-realm.json:28`). |
| POR-006: Add `.gitattributes` for LF normalization | **Verified** | `.gitattributes:1,11-12` sets text normalization/default LF and explicit LF for shell/Python files. |
| POR-007: Add multi-arch Docker build job to CI (buildx, linux/amd64+arm64) | **Verified** | `.github/workflows/ci.yml:183-195` configures QEMU/Buildx and `linux/amd64,linux/arm64`. This is source/config verification only; the multi-architecture build was not executed. |
| POR-012: Create `.env.example` with all `QF_*` vars | **Verified** | `.env.example` documents local and production QF variables; a name comparison against both Compose files found no Compose-used `QF_*` variable absent from the example. Key entries include `.env.example:5-29`. |

#### Phase 3 — UX polish

| Exact checked claim | Status | Source evidence and adjudication |
|---|---|---|
| UX-001: Enable Keycloak registration + forgot password + custom theme | **Verified** | `docker/keycloak/queryforge-realm.json:6,9` enables registration/reset; `docker-compose.yml:101-105` mounts/configures the theme and `docker/keycloak/themes/queryforge/` contains theme assets. Local browser/Keycloak flow was not run in this pass. |
| UX-025: Add “Rerun with same params” endpoint + button | **Verified** | Backend endpoint `src/main/java/com/bakr/queryforge/experiment/ExperimentRunController.java:114-119` recreates from saved run parameters; UI button and handler are at `frontend/src/app/components/run-detail/run-detail.component.ts:51-52,373-388`; API call is at `frontend/src/app/services/api-client.service.ts:80-83`. |
| UX-015: Add column sorting to run history table | **Verified** | `frontend/src/app/components/runs/runs.component.ts:47-51,158-175` provides clickable sort controls and sorting. Sorting applies to the currently loaded page, not the complete history. |
| UX-016: Add pagination UI to run history | **Verified** | `frontend/src/app/components/runs/runs.component.ts:62-65,86-87,116-119,134-139` provides previous/next controls and requests pages of 20. Backend server-side sorting/filter support remains incomplete (see Phase 2). |
| UX-026: Add bulk artifact download (ZIP) | **Verified** | ZIP endpoint is implemented in `src/main/java/com/bakr/queryforge/experiment/ExperimentRunController.java:155-174`; download action and API client are in `frontend/src/app/components/run-detail/run-detail.component.ts:115,391-408` and `frontend/src/app/services/api-client.service.ts:86-88`. |
| UX-019: Structured live logs with timestamps/levels/search | **Partial** | Search and level parsing are at `frontend/src/app/components/run-detail/run-detail.component.ts:20,67,271-278,447-450`, but timestamp is generated on browser receipt (`:272`) and level is inferred from message text (`:274-278`), not supplied as structured backend fields. |
| UX-020: Comparison history + multi-run support | **Partial** | Comparison history is implemented at `frontend/src/app/components/compare/compare.component.ts:130-163,194-205`; comparison form has only baseline and candidate selectors (`:42-52`) and submits a two-run comparison (`:252-275`). Multi-run comparison is not present. |
| UX-021: Plan cost highlighting + diff mode | **Partial** | Cost scoring/highlighting exists in `frontend/src/app/components/plan-explorer/plan-explorer.component.ts:142-159` and `frontend/src/app/components/plan-node/plan-node.component.ts:17-40`. No plan-to-plan diff mode was found; comparison diffs address run environment/metrics instead (`src/main/java/com/bakr/queryforge/experiment/ComparisonService.java:77-160`). |
| UX-018: Add “copy run ID” button with toast feedback | **Verified** | Button/copy/toast handling is at `frontend/src/app/components/run-detail/run-detail.component.ts:36-38,364-370`. |
| UX-017: Link empty state to `/experiments` | **Verified** | `frontend/src/app/components/runs/runs.component.ts:68-72` links the empty run-history state to the experiment catalog. |

#### Phase 4 — Maintainability and CI/CD

| Exact checked claim | Status | Source evidence and adjudication |
|---|---|---|
| MAI-001: Split `ExperimentOrchestrator` into 4 focused classes | **Failed** | `RunScheduler.java:6-9`, `ProcessExecutor.java:6-9`, `ManifestWriter.java:6-9`, and `RunEventPublisher.java:6-9` under `src/main/java/com/bakr/queryforge/experiment/` are empty Spring components with only constructors. `ExperimentOrchestrator.java:45,71,97,152-210,322-348,448-490,528-600,682-768` still owns scheduling, runner execution, recovery, manifest writing, validation, run-ID generation, and log tailing. |
| MAI-002: Split `K6RunnerAdapter` into generator/runner/parser | **Failed** | `src/main/java/com/bakr/queryforge/experiment/runner/K6RunnerAdapter.java:23,47-90,95-107,157-197` retains command construction, process launch, summary lookup, environment conversion, output draining, and script mapping in one class; the requested generator/runner/parser split is not present. |
| MAI-003: Split `ApiService` into client/cache/SSE services | **Verified** | Angular `ApiService`, `ApiClient`, `CacheService`, and `SseService` are separate (`frontend/src/app/services/api.service.ts:117-122`, `frontend/src/app/services/api-client.service.ts:7-10`, `frontend/src/app/services/cache.service.ts:6-9`, `frontend/src/app/services/sse.service.ts:4-7`). Frontend unit tests passed 41/41; production build remains blocked separately. |
| MAI-012: Split `PlanParser` into tokenizer/tree builder/node factory | **Partial** | Helper files exist (`src/main/java/com/bakr/queryforge/artifact/PlanTokenizer.java`, `PlanTreeBuilder.java`, `PlanNodeFactory.java`), but `PlanParser.java:18-119` still owns parsing and tree assembly; its tokenizer/tree-builder fields are not used in that method and helper calls are partly recreated at `:125-133`. |
| MAI-013: Extract comparison report rendering to template engine | **Partial** | A renderer was extracted and called from `ComparisonService.java:163-164`, but `ComparisonReportRenderer.java:9-60,66-108` manually builds Markdown/HTML with `StringBuilder`; no template engine is used. |
| MAI-020: Enable strict TypeScript config | **Verified** | `frontend/tsconfig.json:7-16` sets `"strict": true`. The build independently fails at `frontend/src/app/core/toast/toast.component.ts:11`, so strict mode does not imply a green build. |
| CIC-010: Multi-OS CI matrix | **Verified** | `.github/workflows/ci.yml:14-18` declares Ubuntu, Windows, and macOS. This local validation ran only on Windows; other matrix jobs were not executed here. |
| CIC-011: Dependency caching | **Verified** | Maven cache configured at `.github/workflows/ci.yml:27,45`; npm cache at `:68`. |
| CIC-012: Playwright artifact upload on failure | **Verified** | `.github/workflows/ci.yml:127-137` uploads Playwright report/test-result paths under `if: always()` (including failures). |
| CIC-017: CI job for `local-auth` profile | **Partial** | Job and profile commands exist at `.github/workflows/ci.yml:35-51`; the corresponding local-auth test command failed with 13/197 failures in this worktree. |
| CIC-018: Accessibility audit in CI (axe-core) | **Partial** | CI step exists at `.github/workflows/ci.yml:116-122`, but the scan was blocked by the frontend TS2349 compile failure. `frontend/e2e/a11y.spec.ts:6-67` checks keyboard focus, image alt attributes, compare-form labels, and axe on the overview only; this does not establish a complete WCAG 2.2 AA audit. |

#### Phase 5 — Portfolio polish

| Exact checked claim | Status | Source evidence and adjudication |
|---|---|---|
| MAI-017: ADRs for all key architectural decisions | **Partial** | Three ADRs exist: `docs/adr/0001-oidc-vs-jwt.md`, `0002-sse-vs-websocket.md`, and `0003-k6-as-runner.md`. No complete decision inventory/traceability establishes that all key decisions are documented. |
| PER-008: Bundle size budgets + analysis | **Partial** | `frontend/angular.json:59-69` sets initial/style warning/error budgets. No bundle-analysis output/tool was found; the build failed before bundle output could be assessed. |
| CIC-016: Release automation | **Verified** | `.github/workflows/release.yml:4-21` triggers on version tags and creates a GitHub Release with generated notes. |
| Documentation sync (README, operations, API) | **Partial** | README states Java 25 (`README.md:7`) but still says Compose provides a database-password fallback (`README.md:264`); `docs/operations.md:107-112` also describes an in-memory run-history snapshot and password fallback inconsistent with the inspected persistence/configuration. `docs/renovation/05-api-contract.md:72-188` omits implemented rerun, ZIP, and comparison-history retrieval routes. |
| DB-backed runs store: migrate file-based `ExperimentRun` to JPA entity for true DB-level pagination/sorting/filtering, transactional writes, and concurrency safety | **Partial** | Entity/repository/persistence components exist (`ExperimentRunEntity.java:15-50`, `ExperimentRunRepository.java:10-12`, `RunPersistenceService.java:20-51`). But `V3__create_experiment_runs.sql:4-20` is not wired into app/Compose initialization; both Compose files mount only V1 (`docker-compose.yml:17`, `docker-compose.production.yml:18`), and no Flyway/Liquibase wiring was found. Filter/template paging still loads lists (`RunPersistenceService.java:36-40,51`; `ExperimentRunService.java:61-69`), `save` is a plain `repo.save` (`RunPersistenceService.java:20-21`), and the entity has no `@Version` field. Live database schema/state was not checked. |
| Accessibility audit (WCAG 2.2 AA) — automated axe-core in CI; inputs reviewed for labels; no critical violations in automated scan | **Blocked** | CI and a limited axe spec exist, but the local Playwright scan could not start because Angular compilation fails. No axe results, comprehensive manual label review, or WCAG 2.2 AA audit result can be claimed. |
| POR-011: Sync Java version in README (25 not 21+) | **Verified** | `README.md:7` states Java 25; `pom.xml:20` sets `java.version` to 25. |

### Risks and recommended gap-closure order

1. **Resolve credential hygiene first:** stop tracking local cookie/secret artifacts and verify Git history for any non-fixture credentials before public release. The assessed client secret is the documented dummy local fixture; `cookies.txt` contains only loopback XSRF cookie metadata in the parsed jar. Never treat that limited assessment as proof that any other historical secret is harmless.
2. **Restore deterministic frontend CI:** synchronize `package-lock.json` with `package.json`, then fix the `toasts()`/Observable template mismatch; rerun clean `npm ci`, frontend tests/build, and axe.
3. **Repair the `local-auth` test path:** triage its 13 failing tests and rerun the exact profile command before treating the authenticated CI job as green.
4. **Wire and complete durable run persistence:** ensure V3 schema is applied in supported deployments, move state/template filtering and pagination into repository queries, and establish transactional/concurrency behavior before asserting database-backed pagination guarantees.
5. **Close fidelity gaps:** complete base URL coverage, production image digest pinning, source-timestamped structured logs, multi-run/plan-diff behavior if required, functional extraction of maintainability helpers, and documentation/ADR coverage.
6. **Run gated validations only in appropriate environments:** full multi-OS/multi-arch CI, OWASP Dependency Check with its authorized key, and isolated live-stack/k6 acceptance were not run here. Do not infer production or live-stack readiness from the configuration-only Compose checks.

**Overall adjudication:** the historical Phase 1–5 checkbox claims are not all complete. Several are verified at source/config level, but there are partial and failed claims and blocked validation checks. This audit section is the point-in-time baseline for gap closure, not a remediation record.

## Phase 1–5 Implementation and Verification Update (2026-11-01)

### Scope and provenance

- **Source commit:** `3153b5a9760c788b1b71f4faa618f96695d9da08`; this isolated worktree remains at that exact commit with uncommitted task changes. The primary checkout at `D:\QueryForge_Bakr101_2026` was not modified. No fixes were committed, deployed, or applied to a shared stack.
- **Scope:** only the checked Phase 1–5 claims above. The original findings and checkbox history are preserved verbatim; this section is the current implementation adjudication and supersedes the earlier verification statuses where evidence has changed.
- **Docker safety:** the user's desktop-started stack was inspected read-only. Before and after the checks, the only listed container was `qf-docker-proxy`. No application Compose services were started, stopped, restarted, reused, or mutated. The PostgreSQL test used a disposable Testcontainers container which was cleaned up by Testcontainers.
- **Status meanings:** **Verified** means implementation/configuration and available targeted evidence support the claim; jobs not executed are called out explicitly. **Partial** means the claim still has a substantive gap. **Blocked** means a necessary validation was not safely or successfully performed. **Not verified** means evidence was insufficient.

### Item-by-item current status

#### Phase 1 — Security and reproducibility

| Checked claim | Status | Current source evidence and limits |
|---|---|---|
| SEC-012: Re-enable CSRF in `LocalSecurityConfig` | **Verified** | `src/main/java/com/bakr/queryforge/config/LocalSecurityConfig.java:17-22` enables cookie CSRF protection with narrow exemptions. Default Maven tests passed; the dedicated `local-auth` security test also passed. |
| SEC-013: Dedicated minimal-permission `ROLE_RUNNER` | **Verified** | `src/main/java/com/bakr/queryforge/config/ProductionSecurityConfig.java:157-181` assigns `ROLE_RUNNER` and restricts runner requests to the intended read-only product routes. |
| SEC-017/018: Remove weak Compose password defaults | **Verified** | `docker-compose.yml:8,91` requires database and Keycloak-admin passwords; production consumes secret files at `docker-compose.production.yml:5-8,257-265`. Both Compose files passed config-only validation with disposable inputs; no services were launched. |
| SEC-001/011: Remove repository secrets and require environment/Docker secrets | **Partial** | Current source removes `cookies.txt`, `secrets/qf_oidc_client_secret.txt`, and the literal realm JSON; `docker/keycloak/queryforge-realm.json.template:27` uses a placeholder, and `docker/keycloak/render-realm.sh:5-34` reads a mounted secret file and validates it. The historical Git objects are not rewritten. The tracked client-secret file was safely compared without displaying its content and matched the local dummy realm fixture; `cookies.txt` was a loopback XSRF-cookie entry. No value was identified as a production secret. If either value was reused or is not the documented dummy fixture, external invalidation/rotation and history cleanup remain owner actions; neither was performed. |
| SEC-005: Authorize the SSE endpoint | **Verified** | `src/main/java/com/bakr/queryforge/experiment/ExperimentRunController.java:75-83` protects run events with operator authorization; security tests passed. No live OIDC flow was exercised. |
| SEC-006: Run-scoped artifact endpoint and deprecation of global endpoint | **Verified** | `ExperimentRunController.java:142-176` serves artifacts under a run and ZIP endpoint; `ArtifactController.java:18,27-38,50-62` deprecates the global route and emits successor/deprecation headers. Controller tests are included in the passing Maven suite. |
| SEC-010: OWASP Dependency Check and `npm audit` in CI | **Verified** | `.github/workflows/ci.yml:53-54,77` configures both scanners; `npm audit --audit-level=high` reported 0 vulnerabilities. OWASP Dependency Check itself was not executed locally because the CI invocation requires its authorized NVD API key. |
| REP-001/007: Deterministic data generation and k6 randomness | **Verified** | `scripts/generate-data.py:17,23-29,43-62` seeds per-row RNG; `benchmark/k6/common.js:6-9,37-39` supplies seeded random selection used by the workload scripts. Two generated temporary CSVs for the same rows/seed had identical SHA-256. No k6 workload was run. |
| REP-004: Full environment fingerprint in run manifest | **Verified** | `src/main/java/com/bakr/queryforge/experiment/EnvironmentFingerprintService.java:45-115,125-157` records app commit, Java/vendor, OS/architecture, database/version, dataset/index state, CPU/memory, runner name/version/source hash, and a canonical configuration hash. Fingerprint capture remains explicitly best-effort when the database is unavailable. |
| REP-005: Pin Docker images to SHA digests | **Verified** | Local and production Compose images are digest-pinned, including `docker-compose.yml:4,86,139,150` and `docker-compose.production.yml:3,114,142,167,191`. Image pulls/builds were not part of this check. |

#### Phase 2 — Performance and portability

| Checked claim | Status | Current source evidence and limits |
|---|---|---|
| PER-009: Database-level run pagination with `Pageable` | **Verified** | `src/main/java/com/bakr/queryforge/experiment/RunPersistenceService.java:53-87` applies filter `Specification`, allowlisted sort, and `PageRequest` in the repository query. The disposable PostgreSQL integration test passed all 5 tests, including migration and pagination coverage. |
| PER-001/002: Resolve run-history N+1 / remove unbounded lookups | **Verified** | `ExperimentRunEntity.java:15-50` stores run/artifact data without a lazy artifact relation, while run reads use pageable repository queries (`RunPersistenceService.java:53-87`). The specific per-run artifact N+1 described in the original finding is not present in this model. |
| PER-006: Tune HikariCP for benchmark concurrency | **Verified** | `src/main/resources/application.properties:6-12` configures pool capacity and timeout values for the stated workload. |
| POR-001/008: Configurable callback/API/runner base URLs | **Verified** | `docker-compose.yml:41-42,103` passes configurable runner and web origins; `docker/keycloak/render-realm.sh:21-34` renders the configured origin. Frontend API base configuration is in `frontend/src/app/services/api-client.service.ts:10`; runner URL resolution is in `K6CommandGenerator.java:71-91`. Custom-origin realm rendering passed with a disposable dummy secret; no user credential was used. |
| POR-006: LF normalization | **Verified** | `.gitattributes:1,11-12` enforces normalized text and LF for scripts. `git diff --check` passed; Git emitted only existing working-copy CRLF normalization warnings for other files. |
| POR-007: Multi-architecture Docker CI | **Verified** | `.github/workflows/ci.yml:197-208` configures QEMU/Buildx for `linux/amd64,linux/arm64` for backend and frontend images. The actual Buildx builds were not executed locally. |
| POR-012: `.env.example` covers QF configuration | **Verified** | `.env.example:1-47` lists local and production QF variables, including production-only values as commented entries. Config validation with only this local example correctly lacks required production values; the production config passed when supplied disposable required values. |

#### Phase 3 — UX polish

| Checked claim | Status | Current source evidence and limits |
|---|---|---|
| UX-001: Registration, password reset, and custom Keycloak theme | **Verified** | `docker/keycloak/queryforge-realm.json.template` enables the account features; `docker/keycloak/themes/queryforge/` contains the theme and Compose mounts/configures it at `docker-compose.yml:101-108`. No interactive browser-to-Keycloak authentication was run. |
| UX-025: Rerun with the original parameters | **Verified** | `ExperimentRunController.java:114-119`, `frontend/src/app/components/run-detail/run-detail.component.ts:51-52,377-389`, and `frontend/src/app/services/api-client.service.ts:80-83` implement the endpoint, action, and request. |
| UX-015: Sort run-history columns | **Verified** | `frontend/src/app/components/runs/runs.component.ts:47-52,158-175` provides clickable sort controls; backend query sort is applied in `RunPersistenceService.java:57-87`. |
| UX-016: Run-history pagination UI | **Verified** | `runs.component.ts:62-65,89,116-139` exposes previous/next and page sizing and requests server pages. |
| UX-026: Bulk artifact ZIP download | **Verified** | `ExperimentRunController.java:155-176` creates the run-scoped archive; `run-detail.component.ts:115,391-408` downloads it. |
| UX-019: Structured searchable live logs with timestamp and severity | **Verified** | `RunEventBus.java:49-67` emits server-captured timestamp, normalized severity, and message; `run-detail.component.ts:232-281,447-454` renders and searches the structured event data. `RunEventBusTest` covers event fields. |
| UX-020: Comparison history and multi-run comparison | **Verified** | `frontend/src/app/components/compare/compare.component.ts:47-55,130-163,252-300` selects one baseline and multiple candidates, requests each comparison, and loads history. |
| UX-021: Plan cost highlighting and diff | **Verified** | `frontend/src/app/components/plan-explorer/plan-explorer.component.ts:42-67,184-230,260-293` compares plans and highlights estimated-cost changes. |
| UX-018: Copy run ID with toast feedback | **Verified** | `frontend/src/app/components/run-detail/run-detail.component.ts:36-38,364-370` provides the copy action and toast. |
| UX-017: Empty state links to experiments | **Verified** | `frontend/src/app/components/runs/runs.component.ts:68-72` links the empty run state to the experiment catalog. |

#### Phase 4 — Maintainability and CI/CD

| Checked claim | Status | Current source evidence and limits |
|---|---|---|
| MAI-001: Split `ExperimentOrchestrator` into focused classes | **Verified** | `ExperimentOrchestrator.java:97,210-245,439` delegates scheduling/execution/manifest work to `RunScheduler`, `ProcessExecutor`, and `ManifestWriter`; event publication is delegated to `RunEventPublisher`. |
| MAI-002: Split k6 generator, process runner, and summary parser | **Verified** | `K6RunnerAdapter.java:48-49,88-99` composes `K6CommandGenerator`, `K6ProcessRunner`, and `K6SummaryParser`; the adapter delegates each responsibility. |
| MAI-003: Split frontend API client/cache/SSE concerns | **Verified** | `frontend/src/app/services/api.service.ts:5,117-122`, `api-client.service.ts:7-10`, `cache.service.ts:6-9`, and `sse.service.ts:4-7` show distinct services. |
| MAI-012: Split plan parser into tokenizer/tree builder/node factory | **Verified** | `src/main/java/com/bakr/queryforge/artifact/PlanParser.java:8-12,36-80` uses `PlanTokenizer`, `PlanNodeFactory`, and `PlanTreeBuilder`; implementations reside in separate files. |
| MAI-013: Extract comparison rendering to a template engine | **Verified** | `ComparisonReportRenderer.java:14-76` renders both formats through Thymeleaf; `src/main/resources/templates/comparison-report.html` and `comparison-report.md` hold presentation. `ComparisonService.java:170-171` delegates both outputs. `ComparisonServiceTest` now also asserts Markdown output contains no unresolved Thymeleaf directives. |
| MAI-020: Strict TypeScript | **Verified** | `frontend/tsconfig.json:12,20-21` enables strict checks. `npm run build:analyze` completed successfully after fixing the earlier toast template type error. |
| CIC-010: Multi-OS CI matrix | **Verified** | `.github/workflows/ci.yml:14-18` configures Ubuntu, Windows, and macOS. This worktree ran on Windows only; other matrix runners were not executed. |
| CIC-011: Dependency caching | **Verified** | `.github/workflows/ci.yml:27,45,68` configures Maven and npm dependency caches. |
| CIC-012: Upload Playwright artifacts on failure | **Verified** | `.github/workflows/ci.yml:136-145` uploads reports/results under an always-running condition. |
| CIC-017: CI local-auth test job | **Verified** | `.github/workflows/ci.yml:35-51` compiles the profile and targets `LocalAuthenticatedSecurityConfigTest`. That focused command passed 2/2 tests. The broader full-suite local-auth command still fails 13 tests because unauthenticated functional tests run under the authenticated profile. |
| CIC-018: Axe accessibility audit in CI | **Verified** | `.github/workflows/ci.yml:125-128` runs Playwright axe checks; `frontend/e2e/a11y.spec.ts:50-68` scans `/`, `/experiments`, `/runs`, and `/compare`. The four-route scan passed with no violations. This does not constitute a complete manual WCAG audit. |

#### Phase 5 — Portfolio polish

| Checked claim | Status | Current source evidence and limits |
|---|---|---|
| MAI-017: ADRs for all key architectural decisions | **Verified (bounded inventory)** | `docs/adr/README.md:1-21` explicitly defines seven architecture-level decision areas and maps each to an accepted ADR (`0001`–`0007`). New ADRs `docs/adr/0005-modular-backend-and-evidence-browser.md`, `0006-offset-and-keyset-query-paths.md`, and `0007-required-config-and-mounted-secrets.md` cover the previously unrecorded service boundary, query paths, and deployment-secret delivery. `docs/architecture.md:5-32,72-78` now reflects the implemented stack and points to the inventory. This is a bounded inventory, not a claim that every implementation choice has an ADR. |
| PER-008: Bundle budgets and analysis | **Verified** | `frontend/angular.json:59-69` sets warning/error budgets; `frontend/package.json:8` exposes `build:analyze`. `npm run build:analyze` succeeded and emitted `frontend/dist/frontend/browser-stats.json`. |
| CIC-016: Release automation | **Verified** | `.github/workflows/release.yml:4-21` creates a GitHub Release from version tags. The release workflow itself was not triggered. |
| Documentation sync (README, operations, API) | **Verified** | `README.md:7,41-116`, `docs/operations.md:115-159`, and `docs/renovation/05-api-contract.md:107-219` were updated for Java 25, required secret/config inputs, the V1/V2 baseline and V3/V4 run store, pagination/sorting, rerun, ZIP, comparison history, and structured log events. `docs/architecture.md:5-32,72-78` now also reflects the implemented service and browser boundaries. This was a targeted consistency pass, not a complete API contract conformance test. |
| DB-backed runs with DB pagination, transactional writes, and concurrency safety | **Verified** | `ExperimentRunEntity.java:15-50` includes persisted run/artifact/version fields; `RunPersistenceService.java:31-87` uses transactions, filtering, sorting, and `PageRequest`; migrations are under `db/flyway/`, configured in `application.properties:18-20`, with optimistic locking at `ExperimentRunEntity.java:50`. The approved V1/V2 baseline is preserved and V2 remains manually managed. Testcontainers verified baseline 2, V3/V4 application, no automatic V2 index, pagination, import idempotency, and concurrent updates. No production database was touched. |
| Accessibility audit: WCAG 2.2 AA, reviewed labels, no critical violations | **Partial** | Expanded `frontend/e2e/a11y.spec.ts` scans six routes in light and dark modes, checks labels across the form-bearing routes, skip-link keyboard focus/focus indicator, toast naming/dismissal, and image alt attributes. Axe reports no tagged WCAG A/AA violations. No manual screen-reader test (NVDA unavailable) or exhaustive human WCAG 2.2 AA conformance review was performed; do not claim full conformance. |
| POR-011: README Java version is synchronized to Java 25 | **Verified** | `README.md:7` states Java 25 and `pom.xml` sets Java 25. |

### Executed validation record

These are implementation-worktree results at the source commit stated above. Earlier historical failures are retained in the previous audit section; where a blocker was fixed, the newer result below supersedes it.

| Exact command / check | Result |
|---|---|
| `mvn -B -ntp test` | **Passed:** 202 tests, 0 failures, 0 errors, 0 skipped. This full-suite run preceded the final report-template-only refinement; that refinement was separately covered by the focused report tests below. |
| `mvn -B -ntp '-Dtest=ComparisonServiceTest,ComparisonControllerTest' test` | **Passed after final renderer edit:** 28 tests, 0 failures/errors/skips; verifies Markdown/HTML rendering and controller exports. |
| `mvn -B -ntp -Dtest=PostgresIntegrationSmokeTest test` | **Passed with Docker:** 5 tests, 0 failures/errors/skips. Testcontainers launched isolated disposable PostgreSQL; logs show Flyway baseline v2 and migrations v3/v4. |
| `mvn -B -ntp '-Dspring.profiles.active=local-auth' '-Dtest=LocalAuthenticatedSecurityConfigTest' test` | **Passed:** 2 tests. |
| `mvn -B -ntp test '-Dspring.profiles.active=local-auth'` | **Failed:** 13 failures out of 199 tests; unauthenticated product/filter functional tests are incompatible with this profile. CI now runs the focused security class rather than misapplying the profile to all functional tests. |
| `npm ci` (in `frontend`) | **Passed:** 317 packages installed; 318 audited. This supersedes the earlier lockfile mismatch after the lockfile was synchronized. |
| `npm test` (in `frontend`) | **Passed:** 41/41 unit tests. |
| `npm run build:analyze` (in `frontend`) | **Passed:** Angular build completed and emitted `frontend/dist/frontend/browser-stats.json`; the prior TS2349 toast template error is resolved. |
| `npm audit --audit-level=high` (in `frontend`) | **Passed:** 0 vulnerabilities reported. |
| `$env:PLAYWRIGHT_PORT='43129'; npx playwright test e2e/a11y.spec.ts` (in `frontend`) | **Passed:** 4 Playwright tests; axe scanned four routes with no reported violations. Vite logged expected API proxy connection refusals because no backend was running. This was not full-stack E2E or full WCAG certification. |
| `docker compose --env-file .env.example config --quiet` | **Passed:** local Compose config only; no services started. |
| `docker compose --env-file .env.example -f docker-compose.production.yml config --quiet` with disposable required `QF_*` values and secret-file paths | **Passed:** production config only; no secrets or production services were used. A first attempt with only the local example correctly failed because production-only required variables were unset. |
| `docker ps --format '{{.Names}} {{.Status}} {{.Ports}}'` | **Passed read-only inspection:** only `qf-docker-proxy` was listed before and after the isolated test. |
| `git diff --check`; `git rev-parse HEAD` | **Passed:** no whitespace errors; HEAD remains `3153b5a9760c788b1b71f4faa618f96695d9da08`. Git printed CRLF-normalization warnings for existing working-copy files, not diff-check failures. |

### Remaining risks and action order

1. **Credential history:** current worktree removes the tracked local cookie/secret artifacts, but this does not remove their historical Git blobs. The client secret assessed was the documented dummy fixture; the cookie was a loopback XSRF entry. If either was reused or is not a dummy/local value, the owner must invalidate/rotate it and decide on history cleanup before publishing; no credential action was taken here.
2. **Partial audit claims:** perform an exhaustive human WCAG 2.2 AA review (including a screen reader) and run OWASP Dependency Check with an authorized CI NVD API key. Do not equate automated axe coverage with comprehensive conformance.
3. **Unexecuted environment-specific checks:** multi-OS jobs and actual multi-architecture builds were inspected but not run locally; no k6 workload, interactive Keycloak flow, production database, production deployment, or live application stack was exercised. Keep those checks isolated and non-production when scheduled.
4. **Auth-profile suite:** the focused security tests pass, but the full local-auth profile remains red for 13 tests; either keep the CI scope deliberately focused as configured or make the incompatible functional tests authenticate before expanding that job.

**Current outcome:** checked Phase 1–5 claims are not all complete. The substantive implementation and targeted validation are recorded above; claims explicitly marked **Partial** remain open. No Phase 6–7 work was undertaken.

## Accessibility Follow-up (2026-11-01)

Expanded the axe-core and interaction coverage for the checked WCAG 2.2 AA claim. The audit now scans the overview, experiment catalog, run history, run detail, plan, and comparison routes in both light and dark themes. It waits for the application entrance transition to complete before measuring contrast so the test evaluates stable rendered colors. The first expanded pass found an invalid ARIA `row` role on a div with button children; that role was removed. It also exposed insufficient status/cost badge contrast and an accessible-name mismatch on the run-ID copy button. These were addressed with theme-aware status colors, warning/danger foreground-on-background badges, and an explicit "Copy run ID" name. The muted-text token was darkened for stronger light-theme contrast.

**Validation:** from `frontend`, `$env:PLAYWRIGHT_PORT='43141'; npm run e2e -- e2e/a11y.spec.ts` passed **6/6 tests**. Axe reported no tagged WCAG A/AA violations on all six routes in either theme; the same suite checked programmatic labels on catalog/history/run-detail/plan/compare, first-tab skip-link focus and its focus indicator, toast dismissal naming/behavior, and image alt attributes. The scan waits for the main entrance transition to complete before checking contrast. `npm run build:analyze` passed after the final accessibility edits; Angular still reports existing optional-chain and run-detail CSS-budget warnings. Manual screen-reader testing remains unavailable (NVDA is not installed), so this does not establish full WCAG 2.2 AA conformance.

## MAI-017 Architecture Decision Inventory Follow-up (2026-11-01)

Added `docs/adr/README.md` as the explicit, bounded inventory for the system-level decisions documented in `docs/architecture.md`. The index maps seven areas—service/browser boundaries, query strategy, identity, live events, benchmark runner, persistent run history, and deployment configuration/secrets—to accepted ADRs 0001–0007. New ADRs 0005–0007 record the modular backend/separate browser boundary, distinct offset/keyset paths, and required configuration/mounted-secret approach. Updated the architecture overview to remove stale claims that the system has no auth and to describe the implemented Angular, Keycloak, SSE, and experiment-run layers.

**Validation:** verified every relative Markdown link in `docs/adr/README.md` resolves to an existing file; checked the seven inventory rows map one-to-one to ADR 0001–0007. Documentation-only changes; no build was needed.