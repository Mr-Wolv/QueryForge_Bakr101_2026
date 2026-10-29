# 02 — Requirements Matrix

## 2.1 Legend

| Column | Meaning |
|--------|---------|
| ID | Stable requirement identifier (FR-001, NFR-001, etc.) |
| Priority | P0 = must have for v1, P1 = must have for v2, P2 = gated |
| Source | Where this requirement originates (plan, experiment evidence, user need) |
| Acceptance | Verifiable condition that must be true |

## 2.2 Functional Requirements

| ID | Description | Priority | Source | Acceptance |
|----|-------------|----------|--------|------------|
| FR-001 | Experiment catalog lists only registered templates. Each template includes stable ID, purpose, required environment, allowed parameter ranges, target tables, estimated duration, destructive/read-only classification, and outputs. | P0 | User need, safety | Every visible template maps to one reviewed implementation. Unknown IDs return documented 404. UI never constructs shell commands from arbitrary input. |
| FR-002 | Environment/readiness view shows API status, database reachability, dataset row count, active schema/migration version, optional tool availability (k6), and environment fingerprint. | P0 | NFR-003 | Dependency failures are reported by name. Health/readiness checks are read-only and do not run benchmarks implicitly. |
| FR-003 | Experiment run lifecycle: user can launch a registered experiment, see a run identifier and state (`QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT`), and open its details. | P0 | User need | Run is not marked successful until expected measurements and artifacts exist. Failed runs record bounded error summary. Duplicate start requests with same client request ID do not launch duplicate work. |
| FR-004 | Bounded parameter profiles expose only parameters with documented experimental meaning. Values have server-side allow-lists and numeric limits. | P0 | Safety | Invalid or out-of-range input rejected with field-level 400. Validation occurs at backend even when client bypasses UI. |
| FR-005 | Baseline/candidate comparison: users select two compatible runs and compare configuration, environment fingerprint, latency percentiles, throughput, request/error count, and key plan properties. | P0 | User need | Comparison warns when runs differ in ways that make results non-comparable. Percentage delta not presented as controlled result when runs are incompatible. |
| FR-006 | Measurement detail: for each run, show p50/p95/p99 where measured, throughput, errors/timeouts, execution duration, and workload parameters. Do not invent values not captured by benchmark. | P0 | Integrity | Unavailable metrics shown as unavailable, not zero. Graph labels include units and tested conditions. Results link to raw JSON/CSV evidence. |
| FR-007 | Query-plan explorer: render captured plan data in readable tree or structured view, with raw-text/JSON view. Explain nodes from actual plan. | P0 | User need | Parser tolerates unknown plan node types and preserves raw evidence. Rendering failure cannot destroy original artifact. |
| FR-008 | Immutable run artifacts: each completed run produces a manifest containing template version, parameters, environment fingerprint, start/end times, summary metrics, artifact checksums, and outcome. | P0 | Reproducibility | Completed artifacts are immutable; re-running creates new run ID. Report reproducible from stored artifacts without rerunning benchmark. |
| FR-009 | Failure and cancellation behavior: UI communicates timeout, setup failure, workload failure, or cancellation separately. Long-running jobs do not block ordinary read-only endpoints. | P0 | Reliability | Cancellation stops scheduling new work and waits for runner to terminate safely. Hard time limit enforced. If safe cancellation cannot be proven, cancel control is omitted and bounded automatic timeout is documented. |
| FR-010 | Export report: Markdown or HTML report plus raw JSON/CSV artifacts, summarizing environment, hypothesis, method, result, limits, and trade-offs. | P1 | User need | Export states whether comparison is compatible and includes reproduction details. Text output is escaped. |
| FR-011 | Explainable optimization hints: small number of deterministic hints grounded in plan features and measured workload data. | P2 | Roadmap | Hints labelled as hypotheses, not automatic fixes. Applying index requires explicit action in isolated lab followed by measured before/after experiment. No hint changes external production database. |

## 2.3 Non-Functional Requirements

| ID | Description | Priority | Source | Acceptance |
|----|-------------|----------|--------|------------|
| NFR-001 | Reproducibility: fixed seed and deterministic data generation. Capture exact dataset row count, migration/index state, application commit, PostgreSQL version, Java/runtime version, relevant resource limits. | P0 | Thesis | One baseline and one optimized measurement reproducible from fresh database. |
| NFR-002 | Measurement integrity: control-plane writes do not occur inside measured request interval unless explicitly part of workload. Do not execute parallel benchmark runs against same target DB by default. | P0 | Integrity | Workbench API overhead not counted as target-query latency. Baseline migrations and candidate optimizations separable. |
| NFR-003 | Safety: local development binds to loopback by default. Only predefined experiments, SQL templates, parameter sets, and allowed flags may be executed. No user-supplied shell snippet, file path, arbitrary class name, or free-form SQL passed to process runner. | P0 | Safety | Code review confirms no command injection path exists. |
| NFR-004 | Performance and usability: ordinary metadata/read APIs return within 500 ms at p95 with small history. Loading existing report does not trigger benchmark execution. | P1 | UX | Measured via actuator metrics or external probe. |
| NFR-005 | Persistence and recovery: process restart does not corrupt completed report. Incomplete runs marked interrupted/failed on next startup unless runner contract proves safe resumability. | P1 | Reliability | Simulated restart during run leaves artifacts consistent; run marked failed if not resumable. |
| NFR-006 | Accessibility and clarity: charts include units, labels, and text/table equivalent. Every run state expressed in text, not color alone. Core flows keyboard-operable. | P1 | Quality | axe-core or manual keyboard audit passes. |
| NFR-007 | Maintainability: experiment definition separate from HTTP controllers, runner, artifact serialization, and presentation. | P0 | Engineering | Adding experiment requires template, bounded parameter schema, result/artifact contract, tests, and documentation — not bespoke logic across every layer. |
| NFR-008 | Honest claims: every headline benchmark value maps to stored/reproducible evidence and includes conditions. | P0 | Integrity | README headline values traceable to specific experiment/artifact. |
