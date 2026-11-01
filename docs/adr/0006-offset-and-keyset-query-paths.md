# ADR 0006 — Preserve distinct offset and keyset query paths

## Status
Accepted

## Context
QueryForge compares filtered pagination and concurrency behavior under reproducible benchmark
conditions. The offset endpoint and keyset endpoint represent distinct access patterns, and
combining them behind a single implementation would obscure what is being measured.

## Decision
Use Spring Data JPA specifications for filtered offset pagination and a separate parameterized
JDBC query for keyset pagination. Whitelist sort columns, bind all search values as parameters, and
keep the keyset cursor opaque to clients. Preserve the existing manual operator action for the V2
search index so baseline measurements are not silently changed.

## Consequences
- Benchmark results can compare offset and keyset behavior as separate paths.
- Dynamic SQL is limited to validated sort identifiers; data values remain bound parameters.
- The optional V2 index remains an explicit environment change and must be recorded with the
  experiment fingerprint.
- Changes to query shape or index state require corresponding benchmark and architecture
  documentation updates.
