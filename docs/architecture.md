# QueryForge — Architecture

## What this is

A deliberately small Spring Boot 3.5.5 / Java 25 REST service over PostgreSQL 17 whose purpose is to
**measure** database behavior: filtered search, sorted pagination, and concurrency — then to
optimize it via controlled, reproducible experiments.

## Stack

| Layer | Choice |
| --- | --- |
| Runtime | Java 25, Spring Boot 3.5.5 |
| Web | Spring Web (MVC, Tomcat) |
| Data | Spring Data JPA / Hibernate 6.6 + `JpaSpecificationExecutor`; `NamedParameterJdbcTemplate` for keyset |
| DB | PostgreSQL 17 (`pg_stat_statements` preloaded), Docker Compose |
| Load | k6 (constant-arrival and constant-VU scenarios) |
| Tests | JUnit 5, Testcontainers (PostgreSQL) |
| CI | GitHub Actions (build + functional tests) |

## Components

```text
src/main/java/com/bakr/queryforge/
├── controller/
│   ├── ProductController.java        GET /api/products/{id}, /api/products (offset), /api/products/keyset
│   ├── CategoryController.java       GET /api/categories
│   └── ApiExceptionHandler.java      400 / 404 mapping
├── service/
│   ├── ProductService.java           orchestration: point lookup, offset search, keyset search
│   ├── ProductSpecs.java             dynamic filter Specification (only supplied predicates)
│   ├── ProductKeysetRepository.java  native cursor SQL, whitelisted sort column
│   ├── Cursor.java                   opaque base64url (created_at, id) cursor
│   └── SearchParams.java             validated parameters (size ≤ 200, price sanity, sort whitelist)
├── repo/
│   ├── ProductRepository.java        JPA + Specification executor (offset workload)
│   └── CategoryRepository.java
└── entity/                           Product, Category
```

## Request paths

- **Point lookup** — `findById` (PK), 404 via `NotFoundException`.
- **Filtered search (OFFSET)** — `JpaSpecificationExecutor` builds one SQL with only the supplied
  predicates; Spring Data adds `ORDER BY`, `LIMIT/OFFSET` and a `count(*)` for the envelope.
- **Keyset** — hand-written parameterized SQL; the only interpolated token is the sort column,
  validated against a whitelist (`createdAt`, `price`, `id`); cursor predicate
  `(col < :cv OR (col = :cv AND id < :cid))` for DESC. The endpoint itself accepts only
  `sort=createdAt` (the cursor encodes `(created_at, id)`); `price`/`id` remain legal on the
  offset endpoint.

## Data & environment

- **Schema**: [db/migrations/V1__create_products.sql](../db/migrations/V1__create_products.sql) —
  deliberately **no secondary indexes** (the Stage-0 baseline). The optimization index lives in
  `V2__idx_products_search.sql` and is applied **manually** via `scripts/apply-index.sh`, so the
  baseline experiment remains reproducible.
- **Dataset**: `scripts/generate-data.py` (seed=42, per-row RNG → row i is identical at any N) +
  `scripts/load-data.sh` (COPY, single transaction, VACUUM ANALYZE).
- **Compose**: Postgres on host port **5433** (avoids colliding with a local PostgreSQL on 5432);
  optional full-stack `app` profile (`docker compose --profile app up --build`).

## Deliberate omissions

No auth, no caching layer, no search engine, no message broker, no Kubernetes. Every optimization
in this repo must be attributable to a measured bottleneck — infrastructure would confound the
experiments. (See [conclusions.md](conclusions.md) for what *would* be justified next.)
