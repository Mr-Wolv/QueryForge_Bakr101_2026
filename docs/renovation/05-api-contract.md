# 05 — API Contract

## 5.1 Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Accept` | no | `application/json` default |
| `Content-Type` | yes (write) | `application/json` |
| `X-Client-Request-Id` | no | Idempotency key for POST `/api/runs` |
| `X-Request-Id` | no | Server echoes if provided; server generates if missing |

## 5.2 Error Envelope

All errors conform to RFC 7807 `ProblemDetail`.

```json
{
  "type": "string (URI)",
  "title": "string",
  "status": 400,
  "detail": "string",
  "instance": "string (request path)",
  "errors": { "field": ["message"] }
}
```

Common `type` URIs:
- `https://problem.org/validation-error` — 400
- `https://problem.org/not-found` — 404
- `https://problem.org/conflict` — 409
- `https://problem.org/internal` — 500

## 5.3 Endpoint Contracts

### GET /api/experiments

Response 200:
```json
[
  {
    "id": "QF-001",
    "title": "Filtered Search Baseline",
    "purpose": "...",
    "hypothesis": "...",
    "parameters": [
      { "name": "rate", "type": "integer", "unit": "req/s", "min": 1, "max": 1000 }
    ],
    "estimatedDuration": "60s",
    "destructive": false,
    "outputs": ["k6-summary.json", "plan.json"]
  }
]
```

### GET /api/lab/readiness

Response 200:
```json
{
  "api": { "status": "UP" },
  "database": { "status": "UP", "version": "17.x", "rowCount": 1000000 },
  "k6": { "status": "UP", "path": "/usr/bin/k6" },
  "fingerprint": {
    "appCommit": "d4290fd",
    "javaVersion": "25.0.1",
    "postgresVersion": "17.0",
    "checkedAt": "2026-10-29T19:51:00Z"
  }
}
```

### POST /api/runs

Request:
```json
{
  "templateId": "QF-001",
  "parameters": { "rate": "10", "duration": "60s" }
}
```

Headers: `X-Client-Request-Id: uuid` optional.

Response 201:
```json
{
  "id": "QF-001-20261029-abcdef",
  "templateId": "QF-001",
  "state": "QUEUED",
  "startedAt": null,
  "durationMs": null,
  "labels": {},
  "metrics": null,
  "artifacts": []
}
```

Response 409 (duplicate client request id):
```json
{
  "type": "https://problem.org/conflict",
  "title": "Duplicate Request",
  "status": 409,
  "detail": "client request id already used"
}
```

### GET /api/runs

Query params: `page` (default 0), `size` (default 20, max 100), `state` (optional filter).

Response 200:
```json
{
  "content": [ /* RunRun DTOs */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### GET /api/runs/{id}

Response 200: same shape as POST response but populated.

### POST /api/runs/{id}/cancel

Response 202:
```json
{ "id": "...", "state": "CANCELLING" }
```

Response 409: run already terminal.

### GET /api/runs/{id}/artifacts

Response 200:
```json
[
  { "artifactId": "manifest", "contentType": "application/json", "description": "Run manifest" },
  { "artifactId": "plan", "contentType": "application/json", "description": "EXPLAIN plan" }
]
```

### GET /api/runs/{id}/artifacts/{artifactId}

Response 200: binary or text with `Content-Type` matching artifact type.
Response 404: artifact not found.

### POST /api/comparisons

Request:
```json
{ "leftRunId": "...", "rightRunId": "..." }
```

Response 201:
```json
{
  "id": "cmp-...",
  "leftRunId": "...",
  "rightRunId": "...",
  "verdict": {
    "compatible": true,
    "blockers": [],
    "warnings": ["postgres version differs: 17.0 vs 17.1"],
    "diff": { "postgresVersion": "17.0 vs 17.1" }
  },
  "metricsDelta": {
    "p95": { "left": 8180, "right": 130, "unit": "ms" }
  }
}
```

### GET /api/runs/{id}/events

Content-Type: `text/event-stream`.

Event schema:
```
event: state
data: {"state":"RUNNING","at":"2026-10-29T19:52:00Z"}

event: log
data: {"stream":"stdout","text":"running scenario search..."}

event: artifact
data: {"artifactId":"plan","at":"..."}

event: error
data: {"message":"runner process exited 1"}
```
