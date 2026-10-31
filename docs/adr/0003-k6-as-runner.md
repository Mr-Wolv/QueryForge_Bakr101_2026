# ADR 0003 — k6 as benchmark runner

## Status
Accepted

## Context
Need load generation with scripting and metrics export.

## Decision
Use k6 scripts under `benchmark/k6/`, executed by `K6RunnerAdapter`.

## Consequences
- Deterministic seeds via `__ENV.SEED` (REP-002).
- Runner gets dedicated `ROLE_RUNNER` (SEC-013).
