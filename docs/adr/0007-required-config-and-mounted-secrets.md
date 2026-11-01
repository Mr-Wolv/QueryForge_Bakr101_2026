# ADR 0007 — Required deployment configuration and mounted secrets

## Status
Accepted

## Context
Local and production Compose deployments require database and identity-provider credentials.
Fallback passwords and credentials embedded in realm JSON or tracked files can silently turn sample
configuration into deployable weak credentials.

## Decision
Require deployment-specific `QF_*` values instead of weak Compose defaults. Keep the Keycloak realm
as a non-secret template, render its client secret from a mounted Docker secret at startup, and
document local sample values separately from production-only requirements. Do not commit generated
realm files or secret values.

## Consequences
- Compose configuration fails early when required deployment inputs are absent.
- Local development uses explicit, disposable inputs; production operators must provision their
  own values and secret files.
- Secret rotation and removal of already-published historical blobs remain operator/security
  actions; deleting a file from the current tree does not rewrite Git history.
