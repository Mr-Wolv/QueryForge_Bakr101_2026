# Contributing

Thanks for helping improve QueryForge. Please keep changes focused and include tests or clear
reproduction steps for behavior changes.

## Development setup

- Java 25 and Maven
- Node.js 24 and npm for the Angular frontend
- Docker for PostgreSQL integration tests and the local Compose database
- Python 3 and k6 for reproducing performance experiments

Start the local database with `docker compose up -d db`. Set `QF_DB_PASSWORD` to a strong value
before using Compose outside a private local development environment.

## Checks

Run the relevant checks before opening a pull request:

```powershell
mvn -B -ntp test
Set-Location frontend
npm ci
npm run build
npm run e2e:ci
```

The fixture-backed Playwright suite does not need a running backend. The full browser smoke suite
requires the backend on port 8080 and seeded run/artifact data; see [TEST_RUN.md](TEST_RUN.md).
Performance results should include the workload, dataset size, environment, and reproducible
commands—avoid presenting unmeasured estimates as benchmark results.

## Pull requests

- Explain the problem and the behavior changed.
- Include relevant test results and update documentation when setup or usage changes.
- Keep secrets, credentials, generated run artifacts, and machine-specific files out of commits.
- Do not claim performance improvements without reproducible evidence.
