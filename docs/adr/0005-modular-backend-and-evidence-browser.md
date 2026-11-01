# ADR 0005 — Modular backend with separate evidence browser

## Status
Accepted

## Context
QueryForge needs a product API for database workloads and an operator-facing browser for experiment
catalogs, run history, comparisons, and artifacts. Splitting orchestration, product APIs, run
storage, and artifacts into separately deployed services would add network and coordination
variables to a tool intended to measure database behavior.

## Decision
Keep the Java/Spring Boot backend as one modular monolith, with product search, experiment
orchestration, persistence, artifact, and security boundaries represented by packages and focused
services. Deploy the Angular evidence browser separately as a static frontend that communicates
through the backend API. Invoke k6 as an external process from the backend runner adapter.

## Consequences
- Backend features can share the PostgreSQL transaction and security boundaries without distributed
  coordination.
- The frontend and backend can be built and deployed independently while retaining one API contract.
- Benchmark execution remains an explicit child-process boundary and does not run inside request
  handlers.
- New services are justified only when measured scaling, isolation, or ownership needs outweigh the
  additional network and operational variables.
