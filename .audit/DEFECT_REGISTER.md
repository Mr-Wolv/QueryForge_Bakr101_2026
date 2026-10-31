# QueryForge — Defect Register (live)

Established during Phase A baseline. Each row is backed by real execution evidence,
not by the historical `docs/renovation/GAP_ANALYSIS.md` (which is stale — see note).

**Baseline facts (verified 2026-10-31):**
- Backend `./mvnw -B -ntp clean verify` → BUILD SUCCESS, **133 tests, 0 failures, 0 errors, 0 skipped** (incl. real `postgres:17` via Testcontainers).
- Frontend `npm ci && npm run build` → BUILD_OK.
- Authenticated local Compose stack (db, backend `local-auth`, frontend, keycloak, mailpit, prometheus, alertmanager) was already running; backend profile confirmed `local-auth`.

| ID | Component | Severity | Description | Evidence | Resolution | Verification | Status |
|----|-----------|----------|-------------|----------|------------|--------------|--------|
| QF-DEF-01 | Local OIDC login (frontend nginx) | Critical | OAuth2 `redirect_uri` lost its port (`http://localhost/login/...`), Keycloak rejected callback with `Invalid parameter: redirect_uri`. | Browser: Keycloak 400 page; request showed `redirect_uri=http://localhost/login/oauth2/code/queryforge` vs realm-registered `http://localhost:8080/...`. | `frontend/nginx.conf`: derive external host/port from browser `Host` header (`$qf_forwarded_host`/`$qf_forwarded_port` maps) instead of `$host`/`$server_port`. | Verified — full login completes; `/api/auth/session` returns `authenticated:true` for both roles. | **Verified fixed** |
| QF-DEF-02 | Local OIDC login (Keycloak behind path-rewriting proxy) | Critical | Keycloak generated the login-form POST to `/realms/.../login-actions/authenticate` (no `/id/` prefix); nginx served it as static and returned **405** on the POST. | Browser after credential submit landed on `405 Not Allowed` (nginx) at `/realms/queryforge/login-actions/authenticate`. | `docker-compose.yml`: `KC_HTTP_RELATIVE_PATH=/id/` + healthcheck path `/id/health/ready`; `frontend/nginx.conf`: `/id/` `proxy_pass` without trailing slash (stop stripping); `application-local-auth.properties`: token/userinfo URIs use the `/id/` path. | Verified — login form posts to `/id/realms/.../authenticate`; callback completes. | **Verified fixed** |
| QF-DEF-03 | Local OIDC login (backend token verification) | Critical | OIDC client provider specified `authorization-uri`/`token-uri`/`user-info-uri` by hand but omitted `jwk-set-uri`, so Spring could not verify the ID-token signature and the callback failed with `[missing_signature_verifier]` → `/login?error`. | Backend DEBUG log: `Authentication failed ... [missing_signature_verifier] Failed to find a Signature Verifier for Client Registration: 'queryforge'`. | `application-local-auth.properties`: add `spring.security.oauth2.client.provider.queryforge.jwk-set-uri`. | After fix, `missing_signature_verifier` gone; flow advances to token/userinfo. | **Verified fixed** |
| QF-DEF-04 | Local OIDC login (server-to-server issuer mismatch) | Critical | The backend called Keycloak's token/userinfo directly via the `keycloak` service (no X-Forwarded headers). With `KC_PROXY_HEADERS=xforwarded`, Keycloak then derived the issuer from the request host (`keycloak:8080`) while tokens carried `iss=localhost:8080`, so userinfo rejected the token with `401`, and the callback failed with `[invalid_user_info_response]`. A fixed `KC_HOSTNAME` was tried but broke the direct token call (`400`), so it was reverted. | Backend DEBUG log: `[invalid_user_info_response] ... UserInfo Resource: 401 Unauthorized`; manual probe: token via `keycloak:8080` had `iss=http://localhost:8080/...` but userinfo via `keycloak:8080` returned 401. | Route the backend's token/userinfo/JWK calls through the `frontend` nginx proxy (`http://frontend/id/...`, container port 80); pin the `/id/` block's `X-Forwarded-Host/Port/Proto` to the local public address (`localhost:8080`) so Keycloak issues and validates against one issuer for both callers. Reverted the `KC_HOSTNAME` experiment. | Verified — full login completes for viewer (`operator:false`) and operator (`operator:true`); CSRF enforced; viewer denied writes. | **Verified fixed** |

## Note on the historical gap analysis
`docs/renovation/GAP_ANALYSIS.md` (2026-10-29) reports many "critical gaps" that are
**already implemented** in the current code and covered by the passing suite:
`/api/experiments` catalog controller, SSE endpoint + `RunEventBus`, `RunManifest` with full
environment fingerprint/checksums/versions, bounded 1 MiB separate stdout/stderr capture,
`activeRuns` terminal-state cleanup, restart recovery, Jakarta-style parameter validation with
field-level errors, Testcontainers PostgreSQL integration test, plan-explorer/theme/skeleton
frontend components, actuator/Prometheus exposure. These were verified present by reading the
sources and by the 133-test green run. The gap analysis is historical evidence only.
