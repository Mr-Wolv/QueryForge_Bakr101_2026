# ADR 0001 — OIDC (Keycloak) over self-issued JWT

## Status
Accepted

## Context
Authentication could be self-issued JWT or an external IdP. Need SSO, registration, password reset, role management.

## Decision
Use Keycloak (OIDC) for authentication/authorization; Spring Security as OAuth2 client/resource server.

## Consequences
- Registration/password reset enabled in realm (UX-001).
- Local dev uses `local-auth` profile; production uses Keycloak.
- Client secret must come from env/Docker secrets (SEC-001).
