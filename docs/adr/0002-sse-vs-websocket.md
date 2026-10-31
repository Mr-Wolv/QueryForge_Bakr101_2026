# ADR 0002 — SSE over WebSocket for run events

## Status
Accepted

## Context
Run progress streaming. WebSocket gives bidirectional; SSE is simpler, HTTP-friendly, auto-reconnect.

## Decision
Use Server-Sent Events for `/api/runs/{id}/events`.

## Consequences
- Requires authorization on SSE endpoint (SEC-005).
- Client uses EventSource with ref-counting (PER-003).
