# Operations guide

QueryForge is a **local development and portfolio application**, not a public
service. Its API has no authentication. The default host-based backend binds to
loopback, and Compose publishes the application ports on loopback; do not
override those boundaries on an untrusted network.

## Start and inspect the local stack

From the repository root:

```sh
docker compose --profile app up --build -d --wait
docker compose --profile app ps
docker compose port frontend 80
docker compose port backend 8080
```

Ports are dynamically assigned unless `QF_WEB_PORT`, `QF_API_PORT`, or
`QF_DB_PORT` is set. The API health check is available at
`http://127.0.0.1:<api-port>/actuator/health`. For service diagnostics:

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

## Readiness and known operating limits

- Check `/actuator/health` before using the UI; a healthy API does not prove a
  benchmark dataset or k6 is ready.
- Request metrics and Prometheus histograms are exposed locally at
  `/actuator/metrics` and `/actuator/prometheus`. No alerting or external
  monitoring service is configured.
- The in-memory run-history snapshot refreshes every two seconds; newly written
  legacy result files can take up to that long to appear.
- The benchmark runner invokes local tooling and can consume substantial CPU,
  memory, and database capacity. Only run it against a database you control.
- The Compose database password has a development fallback. Set
  `QF_DB_PASSWORD` for shared local environments; this setting is not a
  production secrets-management solution.
- No authenticated access, TLS termination, production database role
  separation, automated backup schedule, or production rollback procedure is
  provided. These are explicit blockers to public deployment.

## Dependency and source checks

GitHub Actions runs backend tests and builds, a frontend dependency audit and
production build, and the first-session browser flow. Pull requests also run
dependency review; CodeQL analyzes Java and TypeScript. Dependabot proposes
weekly updates for Maven, npm, container images, Docker Compose images, and
GitHub Actions. Review each update and its test results before merging.
