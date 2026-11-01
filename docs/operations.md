# Operations guide

QueryForge has two local operating modes. The **development bypass** (default profile) has no
authentication; its backend binds to loopback and Compose publishes application ports on loopback,
so it must not be exposed on an untrusted network. The **authenticated local profile**
(`QF_SPRING_PROFILE=local-auth`) runs the real OIDC login/session/CSRF chain against the bundled
Keycloak; use it for demos and acceptance work. The production-shaped deployment remains a
separate, externally configured stack.

## Start and inspect the local stack

From the repository root:

Copy `.env.example` to `.env` and replace the `QF_DB_PASSWORD` placeholder with a unique local
password. Compose deliberately rejects an unset password instead of supplying a default.

```sh
docker compose --profile app up --build -d --wait
docker compose --profile app ps
docker compose port frontend 80
docker compose port backend 8080
```

Ports are dynamically assigned unless `QF_WEB_PORT`, `QF_API_PORT`, or
`QF_DB_PORT` is set. The API health check is available at
`http://127.0.0.1:<api-port>/actuator/health`. When using the authenticated
profile, also set `QF_WEB_BASE_URL` to the exact UI origin and port; the
Keycloak realm renderer uses it for the OIDC callback and allowed web origin, and
Keycloak advertises the same origin (with `/id`) as its canonical OIDC issuer.
For service diagnostics:

```sh
docker compose --profile app logs --tail=100 backend
docker compose --profile app logs --tail=100 frontend
docker compose logs --tail=100 db
```

Stop the containers while preserving the database:

```sh
docker compose --profile app down
```

Do not add `-v` unless you intend to delete the database volume.

## Back up and verify a database backup

The archive is copied out of the database container using `docker compose cp`,
which avoids shell-specific binary redirection behavior. Keep the resulting
backup outside version control and store a second copy somewhere independent
of the machine running Docker.

```sh
docker compose exec -T db pg_dump -U queryforge -d queryforge -Fc -f /tmp/queryforge.dump
docker compose cp db:/tmp/queryforge.dump ./queryforge-backup.dump
```

To verify that the archive can be restored without touching the active
database, start a separate Compose project with its own disposable volume:

```sh
docker compose -p qf-restore-check up -d db
docker compose -p qf-restore-check cp ./queryforge-backup.dump db:/tmp/queryforge.dump
docker compose -p qf-restore-check exec -T db createdb -U queryforge restore_check
docker compose -p qf-restore-check exec -T db pg_restore -U queryforge -d restore_check --exit-on-error /tmp/queryforge.dump
docker compose -p qf-restore-check exec -T db psql -U queryforge -d restore_check -c "SELECT count(*) FROM products"
docker compose -p qf-restore-check down --volumes --remove-orphans
```

Use a project name not already in use for the restore check. The final command
deletes only that check project's database volume; it must not be changed to
the default Compose project name. For an actual recovery, stop application
containers, preserve the original volume until recovery is verified, and
restore into a clean database or volume. `pg_restore --clean` drops existing
objects and must not be run against the only copy of the data.

The check was executed on 2026-10-31: the scratch database restored 250 products
(ids 1–250) and 4 public tables, matching the live database; the scratch database
and dump were then removed.

## Exercise an alert

With the monitoring profile running, stop the backend and watch both sides:

```sh
docker compose stop backend
# after the 2-minute `for` window: http://localhost:9090/alerts shows firing
# and http://localhost:8025 receives [FIRING:1] QueryForgeBackendUnavailable
docker compose start backend
# the alert resolves and Mailpit receives [RESOLVED]
```

This pipeline was verified end to end on 2026-10-31. The local Alertmanager
config shortens `group_wait`/`group_interval` so delivery is observable; the
production template keeps the standard 30 s / 5 min values.

## Readiness and known operating limits

- Check `/actuator/health` before using the UI; a healthy API does not prove a
  benchmark dataset or k6 is ready.
- In `local-auth`, management endpoints are exposed on container port `8081`
  (`management.server.port`) so Prometheus can scrape `backend:8081` inside the
  Compose network; host port `8081` still maps to the application on `8080`.
- The bundled benchmark runner authenticates with a process-local
  `InternalRunnerToken`, scoped to anonymous `GET /api/products*`. The token is
  rotated on every backend start and is never returned by the API. It is an
  internal convenience for the loopback-only runner; do not reuse this pattern
  for a public deployment.
- Signing out invalidates the application session but does not end the Keycloak
  SSO session. A second sign-in may complete without a password prompt until
  that SSO session expires.
- Request metrics and Prometheus histograms are exposed locally at
  `/actuator/metrics` and `/actuator/prometheus`.
- Run history is stored in PostgreSQL. At backend startup, existing JSON run manifests are imported
  idempotently; keep the `benchmark/runs` volume mounted until the import has completed.
- The database initializes the product schema from V1. Flyway baselines that pre-existing schema at
  version 2, then applies the run-store migrations from `db/flyway` (V3/V4). The performance index
  in `db/migrations/V2__idx_products_search.sql` remains a manual, opt-in operation.
- The benchmark runner invokes local tooling and can consume substantial CPU,
  memory, and database capacity. Only run it against a database you control.
- `QF_DB_PASSWORD`, `QF_KEYCLOAK_ADMIN`, and `QF_KEYCLOAK_ADMIN_PASSWORD` must be set explicitly.
  The local OIDC client secret must be supplied through `QF_OIDC_CLIENT_SECRET_FILE`; the tracked
  `.example` file is a nonfunctional placeholder only.
- The default Compose stack is local-only. A separate production-shaped stack
  is described below, but it has not been deployed against a real identity
  provider, domain, SMTP service, or production host.

## Production-shaped deployment

`docker-compose.production.yml` is deliberately separate from the local stack.
It enables OIDC login, read-only access for ordinary signed-in users, group-gated
operator actions, Caddy-managed HTTPS, private PostgreSQL and monitoring
networks, scheduled database dumps, Prometheus alerts, and SMTP email delivery.
Do not run it as a substitute for the local Compose profile.

Before deploying, prepare a Linux Docker host with a public DNS name pointing to
it, inbound TCP ports 80 and 443 open, an OIDC provider, an SMTP account, and a
secure way to provision secret files. Create secret files outside the checkout
with restrictive permissions; do not commit them. Set these deployment
variables in the environment used for Compose:

| Variable | Required value |
| --- | --- |
| `QF_DOMAIN` | Public DNS name for the application |
| `QF_ACME_EMAIL` | Address for Caddy certificate notices |
| `QF_OIDC_ISSUER_URI` | Exact OIDC issuer URL |
| `QF_OIDC_CLIENT_ID` | OIDC client ID |
| `QF_OIDC_CLIENT_SECRET_FILE` | Path to OIDC client-secret file |
| `QF_OIDC_OPERATOR_GROUPS` | Comma-separated trusted group claim values allowed to run/cancel benchmarks and create comparisons |
| `QF_OIDC_GROUP_CLAIM` | Optional claim name; defaults to `groups` |
| `QF_POSTGRES_ADMIN_PASSWORD_FILE` | Path to a strong PostgreSQL initialization password |
| `QF_APP_DB_PASSWORD_FILE` | Path to the app's read-only database password |
| `QF_MONITOR_DB_PASSWORD_FILE` | Path to the monitoring role password |
| `QF_SMTP_HOST` | SMTP hostname and port, typically port 587 |
| `QF_SMTP_FROM` | Valid sender address |
| `QF_SMTP_USERNAME` | SMTP authentication username |
| `QF_SMTP_PASSWORD_FILE` | Path to the SMTP password file |
| `QF_ALERT_EMAIL` | Alert recipient address |

Register the OIDC callback URI as `https://<QF_DOMAIN>/login/oauth2/code/queryforge`.
Group names must come from the provider's signed ID-token/user-info claim; leave
`QF_OIDC_OPERATOR_GROUPS` empty to keep all signed-in users read-only. Only the
edge service publishes host ports. PostgreSQL, the app backend, metrics, and
alert management do not publish host ports. Do not separately expose backend
port 8081; it is intended only for the internal readiness and metrics checks.

Start and inspect the production-shaped stack:

```sh
docker compose -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.production.yml up --build -d --wait
docker compose -f docker-compose.production.yml ps
docker compose -f docker-compose.production.yml logs --tail=100 edge backend alertmanager
```

The database initializes the app and monitoring roles only when its data
directory is first created. Existing production data requires a separately
reviewed role migration; do not assume init scripts rerun against an existing
volume. Backups are custom-format dumps written daily by default to the local
`production_backups` volume and retained for 14 days. Tune
`QF_BACKUP_INTERVAL_SECONDS` and `QF_BACKUP_RETENTION_DAYS` only after confirming
the host's recovery objectives. This local volume is not an off-host backup:
copy archives to independent protected storage and periodically restore one
into an isolated database before relying on it.

Prometheus evaluates backend availability, server-error rate, request latency,
and PostgreSQL exporter availability. Alertmanager sends email using SMTP with
TLS required. Confirm a real test notification arrives and inspect Prometheus
targets/alerts before treating these controls as operational. No dashboard,
on-call rotation, delivery guarantee, or external monitoring provider is
included.

For rollout, build and start the candidate stack, verify HTTPS, sign-in,
read-only and operator behavior, health probes, monitoring targets, and a
delivered test email before shifting DNS or announcing service. For rollback,
retain the last known-good image tags and database backup, stop new traffic at
the edge, restore the previous application image/configuration, then verify
health and data. Do not roll back by deleting volumes or running destructive
restore commands against the only database copy. Schema migrations must be
backward-compatible with both the old and new application versions.

This deployment still needs real-host evidence: verified certificate issuance,
OIDC login and group claims, successful SMTP delivery, scheduled backup and
restore, external security testing, off-host backup protection, an owned
incident/alert response path, and a rehearsed rollback. Until then, it is
production-shaped configuration, not a public-production readiness claim.

## Dependency and source checks

GitHub Actions runs backend tests and builds, a frontend dependency audit and
production build, and the first-session browser flow. Pull requests also run
dependency review; CodeQL analyzes Java and TypeScript. Dependabot proposes
weekly updates for Maven, npm, container images, Docker Compose images, and
GitHub Actions. Review each update and its test results before merging.
