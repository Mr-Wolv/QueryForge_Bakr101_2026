# ADR 0004 — PostgreSQL run store with a version-2 Flyway baseline

## Status
Accepted

## Context
Run history was stored as JSON manifests, which required loading and filtering the complete
history in application memory. The product schema already existed as an operator-managed V1 SQL
file, while V2 is an optional search-performance index that must remain opt-in for reproducible
baseline measurements.

## Decision
Persist runs in PostgreSQL through JPA, use repository-level filtering and pagination, and apply
optimistic locking for concurrent updates. On startup, import existing manifests idempotently.
Initialize the existing product schema outside Flyway, baseline it at version 2, and keep only the
run-store V3/V4 migrations in Flyway's migration directory. V2 remains a manual operator action.

## Consequences
- Existing V1 product data is preserved; Flyway applies only V3 and V4 to the run store.
- Legacy run manifests remain importable and are not deleted by the backfill.
- Run-history filters, sorting, and pagination execute in the database.
- PostgreSQL integration tests verify the baseline, applied migrations, manual-index omission,
  idempotent backfill, and optimistic concurrency.
- New schema changes must use monotonically increasing Flyway versions in `db/flyway`.
