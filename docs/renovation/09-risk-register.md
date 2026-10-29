# 09 — Risk Register

| ID | Risk | Likelihood | Impact | Mitigation | Contingency |
|----|------|------------|--------|------------|-------------|
| R-01 | JDK 25 / ByteBuddy compatibility breaks Hibernate proxies | Medium | High | Pin `byte-buddy.version=1.17.7` in pom; run CI on every PR with JDK 25. | Fall back to JDK 21 if unresolved; document JDK 25 as experimental. |
| R-02 | Testcontainers cannot reach Docker on Windows | Medium | Medium | Use named pipe config or `QF_DB_URL` override for functional tests. Document in README. | Skip functional tests in CI if Docker unavailable; keep unit + integration tests green. |
| R-03 | Frontend build budget exceeded | Low | Medium | Keep components narrow; lazy-load routes; avoid heavy chart library bloat. | Increase budget in `angular.json` with justification; extract shared chart module. |
| R-04 | Scope creep into DBMS or auto-optimizer | High | High | This plan is the contract. Any new feature requires written change request and phase gate approval. | Freeze scope after Phase 3; create separate repo for educational engine. |
| R-05 | Runner process leaks on timeout | Medium | High | Watchdog destroys process; integration tests cover timeout and cancellation. | Fallback: reduce default timeout, document bounded automatic timeout, omit cancel UI. |
| R-06 | Plan parser breaks on new PostgreSQL versions | Medium | Medium | Tolerate unknown node types; preserve raw artifact always. | Add parser regression tests with plan snapshots from PG 16, 17, 18. |
| R-07 | Chart library introduces security or bundle vulnerability | Low | Medium | Pin versions; review `npm audit` in CI. | Replace with alternative (`ApexCharts`) if unresolved within 1 sprint. |
| R-08 | User rejects editorial direction | Low | High | Review design tokens and component specs with user before implementation. | Iterate on tokens first; components derive from tokens so changes are local. |
