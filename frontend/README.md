# QueryForge frontend

The Angular application is served by Docker as part of the repository's Docker-first startup.
From the repository root, follow the [main quick start](../README.md#quick-start-docker-first) to
start PostgreSQL, the API, and the UI together. Compose assigns the frontend's host port dynamically;
run `docker compose port frontend 80` to find the browser URL.

## Local frontend development

Requirements: Node.js 24 and npm. The dev-server proxy sends `/api` requests to a backend at
`http://localhost:8080`.

```powershell
npm ci
npm start
```

Open `http://localhost:4200`. To use another frontend port, pass it to the dev server:

```powershell
npm start -- --host 127.0.0.1 --port 4201
```

## Build and browser tests

```powershell
npm run build
npm run e2e:ci
```

The production build uses lazy-loaded routes and enforces the initial JavaScript/CSS bundle budget
configured in `angular.json`. The fixture-backed `e2e:ci` suite starts its own frontend server and
does not need a backend. The full `npm run e2e` suite requires the API on port 8080 and seeded run
history; see [TEST_RUN.md](../TEST_RUN.md). For Compose, pin `QF_API_PORT=8080` in `.env` if the
suite needs to reach the API at that fixed host port.
