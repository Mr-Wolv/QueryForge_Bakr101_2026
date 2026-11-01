# Architecture decision inventory

This index defines the bounded set of key architectural decisions for the system described in
[`docs/architecture.md`](../architecture.md). It covers the service boundary and major
cross-cutting choices for identity, event delivery, benchmark execution, data access, durable run
history, and deployment configuration. It does not attempt to record every library, UI feature,
CI implementation detail, or internal class design.

| Decision area | ADR | Current coverage |
| --- | --- | --- |
| Backend/frontend service boundary and deployment topology | [0005 — Modular backend with separate evidence browser](0005-modular-backend-and-evidence-browser.md) | Accepted |
| Product search query paths for offset and keyset workloads | [0006 — Preserve distinct offset and keyset query paths](0006-offset-and-keyset-query-paths.md) | Accepted |
| Production identity and local development security profile | [0001 — OIDC (Keycloak) over self-issued JWT](0001-oidc-vs-jwt.md) | Accepted |
| Live run-event delivery | [0002 — SSE over WebSocket for run events](0002-sse-vs-websocket.md) | Accepted |
| Benchmark workload runner | [0003 — k6 as benchmark runner](0003-k6-as-runner.md) | Accepted |
| Durable run history, migrations, and concurrency | [0004 — PostgreSQL run store with version-2 Flyway baseline](0004-postgres-run-store-with-flyway-baseline.md) | Accepted |
| Local/production configuration and secret delivery | [0007 — Required deployment configuration and mounted secrets](0007-required-config-and-mounted-secrets.md) | Accepted |

These seven areas are the explicit inventory for MAI-017. The list is intentionally bounded to
architecture-level decisions present in the current system; implementation details and future
decisions should be added when they change a listed boundary or introduce a new cross-cutting
choice.
