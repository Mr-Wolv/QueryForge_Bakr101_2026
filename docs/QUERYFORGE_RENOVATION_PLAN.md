# QueryForge — Full-Stack Performance Workbench Renovation Plan

**Plan type:** Controlled product/architecture renovation  
**Repository:** <https://github.com/Mr-Wolv/QueryForge_Bakr101_2026>  
**Plan baseline:** 28 October 2026  
**Primary decision:** Build a user-facing performance workbench around the existing PostgreSQL experiment engine. Do not rewrite PostgreSQL or turn this repository into a general-purpose DBMS.

---

## 1. Executive summary

QueryForge already has a strong technical core. The current repository describes a Java 25 / Spring Boot 3.5.5 REST service over PostgreSQL 17, a deterministic seed-controlled catalog with one million rows, k6 workload scripts, captured `EXPLAIN (ANALYZE, BUFFERS)` plans, and repeatable experiments covering dataset scaling, index selection, pagination, concurrency, and index ordering.

The project has measured results worth preserving. Its README reports that a workload-shaped composite index reduced filtered-search p95 latency from 8.18 seconds to 130 milliseconds at 10 requests/second. It also documents the trade-offs: roughly 60 MB of index storage, write overhead, the different behavior of OFFSET and keyset pagination, and a later throughput plateau caused by the ten-connection pool. The order-first index candidate was measured and deliberately not applied because it optimized a non-bottleneck and imposed another substantial structure.

The missing portfolio layer is an approachable way to explore, repeat, compare, and understand this evidence. The new application should let a developer or reviewer navigate experiments and their artifacts rather than reading the repository as a set of shell scripts and Markdown reports.

**Product statement:** QueryForge is a local-first database performance workbench that runs controlled, predefined PostgreSQL experiments and preserves the evidence needed to explain performance changes and their costs.

The core principle remains: **measure first, optimize from evidence, preserve reproducibility, and never claim a result that the artifacts cannot support.**

## 2. Strategic boundaries

### In scope

- An Angular/TypeScript interface served by or deployed alongside the existing Spring Boot API.
- Experiment catalog with scenario description, workload parameters, expected outputs, safety classification, and reproducibility instructions.
- Run history with state, timestamps, parameters, environment fingerprint, progress, measurements, and artifact links.
- Baseline-versus-candidate comparisons.
- Query-plan inspection, with important nodes and metrics rendered readably while preserving the raw plan.
- Latency/throughput/error graphs and explicit environment labels.
- A safe, allow-listed way to run the existing experiments.
- Export of an experiment report and downloadable raw artifacts.
- Optional, explainable index/query recommendations only after the core workbench is stable.

### Out of scope

- Building a PostgreSQL competitor, SQL engine, storage engine, or general DBMS.
- Arbitrary SQL execution through a public API.
- Arbitrary command execution or a web form that passes user-supplied shell arguments to `ProcessBuilder`.
- Automatic production database changes.
- A general-purpose APM platform, cloud benchmarking marketplace, team collaboration service, billing system, or multi-tenant hosted product in v1.
- Adding Redis, Kafka, Kubernetes, or other infrastructure to a project whose current purpose is to isolate database behavior.
- Replacing the measured PostgreSQL planner with hand-authored optimizer rules.

## 3. DBMS ambition: explicit decision

The phrase “optimized DBMS” can mean several substantially different deliverables:

1. **Performance workbench:** measure real PostgreSQL behavior and prove which configuration/query/index change helps. This is the current project's natural evolution.
2. **Optimization advisor:** analyze workload and plans, then recommend candidate indexes or query changes for a human to test. This is a feasible later layer on top of the workbench.
3. **Educational DBMS:** write a small storage/query engine with a deliberately limited SQL subset to study pages, records, indexes, transactions, locking, and recovery. This is a valid systems-learning project, but it should be a separate repository or an explicitly separate sandbox module with independent success criteria.
4. **Competitive general-purpose DBMS:** pursue correctness, SQL compatibility, concurrency, crash recovery, planner quality, administration, ecosystem behavior, and production reliability. This is too broad to combine with the present CV-focused workbench.

**Decision:** keep QueryForge in category 1, with category 2 as a gated extension. If the educational engine remains personally motivating, create a separate learning project only after the QueryForge v2 workbench reaches its release gate. Do not mix a new database engine into the benchmarked application: doing so would make it harder to know whether a measured result reflects the database, the harness, or the experimental engine.

## 4. Intended users and jobs to be done

### Primary user: developer diagnosing a slow query

Needs to reproduce the workload, understand the plan, compare a candidate change, and explain why it helped or failed.

### Secondary user: reviewer/interviewer

Needs to understand the experiment without a long setup session: view the baseline, see the measured delta, inspect a representative plan, and download evidence.

### Maintainer: project owner

Needs experiments to be repeatable, bounded, inspectable, safe to run, and straightforward to extend without breaking prior evidence.

## 5. Product behavior and functional requirements

Use requirement IDs in issues, tests, and commits so later work stays traceable.

### FR-001 — Experiment catalog (P0)

The UI shall list only registered experiment templates. Each template includes a stable ID, purpose, required environment, allowed parameter ranges, target tables, estimated duration, destructive/read-only classification, and outputs it creates.

**Acceptance:** every visible template maps to one reviewed implementation; unknown IDs return a documented 404; the UI never constructs shell commands from arbitrary user input.

### FR-002 — Environment/readiness view (P0)

Show whether the API is running, whether the target database is reachable, the dataset row count, active schema/migration version, whether optional tools such as k6 are available, and the current environment fingerprint.

**Acceptance:** dependency failures are reported by name and do not appear as a successful empty dashboard. Health/readiness checks are read-only and do not run a benchmark implicitly.

### FR-003 — Experiment run lifecycle (P0)

A user can launch a registered experiment, see a run identifier and state (`QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, or `TIMED_OUT`), and open its details.

**Acceptance:** a run is not marked successful until expected measurements and artifacts exist; failed runs record a bounded error summary; duplicate start requests do not accidentally launch duplicate work when the client retries the same run-creation request.

### FR-004 — Bounded parameter profiles (P0)

Expose only parameters that have a documented experimental meaning, such as dataset scale, offered rate, duration, or virtual-user count. Values have server-side allow-lists and numeric limits.

**Acceptance:** invalid or out-of-range input is rejected with a field-level 400 response; values are validated at the backend even when the client bypasses the UI.

### FR-005 — Baseline/candidate comparison (P0)

Users can select two compatible runs and compare their configuration, environment fingerprint, latency percentiles, throughput, request/error count, and key plan properties.

**Acceptance:** the comparison warns when runs differ in a way that makes the result non-comparable (database version, dataset size, offered load, query definition, index state, hardware/resource limits, or important environment variables). It must not present the percentage delta as a controlled result when the runs are incompatible.

### FR-006 — Measurement detail (P0)

For each run, show measurements and definitions: p50/p95/p99 where measured, throughput, errors/timeouts, execution duration, and workload parameters. Do not invent values that the benchmark did not capture.

**Acceptance:** unavailable metrics are shown as unavailable, not zero. Graph labels include units and the tested conditions. Results link to their raw JSON/CSV evidence.

### FR-007 — Query-plan explorer (P0)

Render captured plan data in a readable tree or structured view, with a raw-text/JSON view. Explain nodes from the actual plan (for example, sequential/parallel scan, bitmap scan, index scan, sort, row estimates, actual rows, loops, buffer reads/hits where present).

**Acceptance:** the parser tolerates unknown plan node types and preserves raw evidence. A rendering failure cannot destroy the original artifact. Never summarize an unmeasured cost as a measured latency result.

### FR-008 — Immutable run artifacts (P0)

Each completed run produces a manifest containing experiment/template version, parameters, environment fingerprint, start/end times, summary metrics, artifact checksums, and outcome. Store query plans and raw measurements as artifacts.

**Acceptance:** completed artifacts are immutable; re-running creates a new run ID. A report can be reproduced from stored artifacts without rerunning the benchmark.

### FR-009 — Failure and cancellation behavior (P0)

The UI communicates timeout, setup failure, workload failure, or cancellation separately. Long-running jobs must not block ordinary read-only endpoints.

**Acceptance:** cancellation stops scheduling new work and waits for the runner to terminate safely; a hard time limit is enforced; a timed-out process cannot linger unnoticed. If safe cancellation cannot be proven in the first version, omit the cancel control and document bounded automatic timeout instead.

### FR-010 — Export report (P1)

Export a Markdown or HTML report, plus raw JSON/CSV artifacts, summarizing the environment, hypothesis, method, result, limits, and trade-offs.

**Acceptance:** export states whether the comparison is compatible and includes enough details to reproduce it; text output is escaped and paths are not exposed to untrusted users.

### FR-011 — Explainable optimization hints (P2)

After the workbench is stable, add rules-based hints from observed plans and workload metadata, such as “large filtered scan with low selectivity: test a candidate index.” Every hint links to the evidence and names its assumptions.

**Acceptance:** hints are labelled as hypotheses, not automatic fixes. Applying an index requires explicit action in the isolated lab, followed by a measured before/after experiment. No hint is allowed to change an external production database.

## 6. Non-functional requirements

### NFR-001 — Reproducibility

- Fixed seed and deterministic data generation remain available.
- Capture the exact dataset row count, migration/index state, application commit, PostgreSQL version, Java/runtime version, and relevant resource limits.
- Comparisons use matching workload definitions and controlled settings.
- Re-run at least one key benchmark multiple times before writing a headline performance claim; disclose the aggregation method and variability.

### NFR-002 — Measurement integrity

- Control-plane writes and report generation must not occur inside the measured request interval unless they are explicitly part of that workload.
- Do not execute parallel benchmark runs against the same target database by default. Serialize experiments unless the experiment itself is a concurrency test.
- Separate the benchmark target from artifact storage where practical; never count workbench API overhead as target-query latency.
- Keep baseline migrations and candidate optimizations separable so a fresh setup can reproduce both states.

### NFR-003 — Safety

- Local development mode binds to loopback by default.
- Only predefined experiments, SQL templates, parameter sets, and allowed flags may be executed.
- No user-supplied shell snippet, file path, arbitrary class name, or free-form SQL is passed to a process runner.
- Any route that triggers a workload is unavailable or strongly protected in a public deployment until a safe isolated runner is implemented.
- Set per-run duration, dataset, concurrency, and resource limits.

### NFR-004 — Performance and usability

For the local portfolio environment, ordinary metadata/read APIs should usually return within 500 ms at p95 with a small history. Loading an existing report should not trigger benchmark execution. Workload runs may take longer but must expose state and a clear estimate or prior measured duration.

### NFR-005 — Persistence and recovery

A process restart must not corrupt an already completed report. Incomplete runs are marked interrupted/failed on next startup unless a specific runner contract proves safe resumability. Do not pretend k6 state or partially captured plans can be resumed if they cannot.

### NFR-006 — Accessibility and clarity

Charts include units, labels, and a text/table equivalent. Every run state is expressed in text, not color alone. Empty, invalid, running, failed, and successful states are distinct. Core flows should be keyboard-operable.

### NFR-007 — Maintainability

Keep the experiment definition separate from HTTP controllers, the runner, artifact serialization, and presentation. Adding an experiment should require a template, bounded parameter schema, a result/artifact contract, tests, and documentation—not bespoke logic across every layer.

### NFR-008 — Honest claims

Every headline benchmark value on the README must map to stored/reproducible evidence and include conditions. A UI graph never substitutes for raw measurements or a written methodology.

## 7. Recommended architecture

### Frontend

- Angular + TypeScript + Angular CLI.
- A small API client with typed DTOs and centralized error handling.
- Separate feature components/routes for catalog, readiness, run details, comparison, and plan inspection.
- Keep charting components narrow and feed them normalized metrics from the API; do not put domain calculations in chart components.

### Backend control plane

Retain the existing Java/Spring Boot service and current product/query APIs. Add bounded experiment-oriented controllers and services rather than rewriting the existing product service.

Proposed package split:

```text
com.bakr.queryforge
  api/              experiment + existing product controllers, DTOs, exception mapping
  experiment/       templates, parameters, orchestration, lifecycle, policies
  runner/           registered runner adapters, process lifecycle, timeouts
  artifact/         manifest, checksums, parsing, immutable storage
  measurement/      normalization and comparison rules
  service/ repo/ entity/ existing catalog workload
```

This is a logical responsibility split, not an invitation to create microservices.

### Runner boundary

The runner may invoke only a named, registered benchmark adapter. Adapter parameters are typed and validated. The browser cannot submit a command. If the first implementation wraps existing scripts, the script name and invocation shape must be fixed in code; user inputs become validated environment variables/arguments, never shell fragments. Keep runner execution local-only until process isolation, resource quotas, and authentication have been designed.

### Artifact storage

For v1, persist run manifests, raw JSON, CSV, and plan captures as immutable artifacts beneath a dedicated runtime directory, with safe generated run IDs and checksum validation. The benchmark dataset database remains the experimental target. Artifact writes happen after the measured interval. If durable multi-user storage is later required, design a separate metadata database rather than mixing workbench writes into the measured query path.

### Target database

Keep PostgreSQL 17 as the initial baseline if the current measured evidence depends on it. A version bump must be a separate experiment because planner behavior and result numbers may change. Continue to use the migration that intentionally starts without secondary indexes and apply the candidate index separately.

## 8. Proposed interface map

1. **Overview:** environment readiness, dataset size, latest experiment, known baseline results, and warnings about the current target database state.
2. **Experiment catalog:** each experiment's hypothesis, steps, parameters, expected artifacts, and duration bounds.
3. **New run:** choose a registered scenario and valid parameters; confirm any action that modifies the isolated lab database.
4. **Run detail:** lifecycle, metrics, errors, manifest, artifact downloads, and raw plan view.
5. **Compare runs:** compatibility checklist, side-by-side metrics, visual deltas, and trade-off summary.
6. **Plan explorer:** parsed plan tree and raw plan JSON/text, with unknown nodes still inspectable.
7. **Evidence library:** filter past runs and download reports/manifests without rerunning anything.

The interface does not need accounts, billing, teams, comments, or a generic SQL editor in the first version.

## 9. API outline

These are proposal-level names; reconcile them with the existing route conventions before implementation.

- `GET /api/experiments` — registered template catalog.
- `GET /api/experiments/{id}` — template and parameter contract.
- `GET /api/lab/readiness` — target database and optional tool status.
- `POST /api/runs` — create a run from an allow-listed template and validated parameter object; support an idempotency key or client request ID.
- `GET /api/runs` — bounded, paginated run history.
- `GET /api/runs/{id}` — state and summary.
- `POST /api/runs/{id}/cancel` — only when cancellation is safely implemented.
- `GET /api/runs/{id}/artifacts` and `/api/runs/{id}/artifacts/{artifactId}` — artifact catalog/download with path-safe ID lookup.
- `POST /api/comparisons` — compare existing run IDs and report compatibility.
- `GET /api/comparisons/{id}` — stored comparison/report if comparison persistence is justified.

Do not expose an endpoint like `/api/run?command=...` or `/api/sql?query=...`.

## 10. Phase plan and gates

### Phase 0 — Baseline and design freeze

Tasks:
- Clean-checkout build and test verification.
- Inventory current scripts, experiment IDs, artifacts, plan formats, and exact reproduction commands.
- Define the authoritative schema for a run manifest and a normalized metrics DTO.
- Define safe execution allow-lists and maximum workload bounds.
- Record a decision: local workbench first; no public runner in v1.

Exit criteria:
- Every existing README headline measurement maps to an experiment/artifact.
- A reviewer can reproduce one baseline and one optimized measurement from a fresh database.
- The workbench can be developed without changing the benchmark semantics.

### Phase 1 — Read-only evidence browser

Tasks:
- Build the Angular shell and overview.
- Load existing experiment metadata and historical result artifacts.
- Add run detail, comparison of already-captured compatible runs, and raw-plan access.
- Add loading, empty, unavailable, and error states.

Exit criteria:
- User can inspect existing experiments and results with no benchmark execution.
- Graphs match raw artifacts and show correct units.
- No new API can execute work or mutate the target DB in this phase.

### Phase 2 — Safe experiment orchestration

Tasks:
- Implement the template registry, typed parameter validation, run-state model, timeout handling, and immutable manifest/artifact creation.
- Add one existing scenario first: filtered search before/after index.
- Serialize runs against the same target DB.
- Record environment fingerprint and compatibility metadata.

Exit criteria:
- One run can be launched through the UI and produces a complete evidence artifact.
- Invalid inputs, duplicate requests, timeouts, and runner failure are tested.
- API endpoints remain responsive during a workload.

### Phase 3 — Experiment breadth and comparison

Tasks:
- Add offset/keyset comparison and dataset/concurrency experiments.
- Add plan parsing that always preserves original artifacts.
- Implement run compatibility rules and explicit “not comparable” behavior.
- Export a report with method, hypothesis, results, limitations, and trade-offs.

Exit criteria:
- The workbench can reproduce the core existing experiments and compare compatible runs.
- Incompatible comparisons are blocked or prominently qualified.
- Historical README data remains traceable to the original reproduction procedure.

### Phase 4 — Optional explainable optimization advisor

Only proceed if phases 1–3 are complete and the core tool works reliably.

Tasks:
- Add a small number of deterministic hints grounded in plan features and measured workload data.
- Have each hint link to the relevant plan nodes and proposed hypothesis.
- Permit applying a proposed index only in the disposable lab and only after explicit confirmation.
- Re-run the experiment and present the measured result and storage/write cost.

Exit criteria:
- Every suggestion has a reason and linked evidence.
- No suggested change is described as “optimal” without measured support.
- A human remains responsible for applying the change.

## 11. Testing and validation strategy

- Unit tests for parameter bounds, template registry, run-state transitions, compatibility checks, percentile/throughput normalization, and manifest versioning.
- Integration tests for artifact creation, checksum verification, path traversal rejection, restart recovery, and route semantics.
- Runner contract tests with a fake executable/adapter for success, nonzero exit, timeout, partial output, cancellation, and process cleanup.
- PostgreSQL integration tests for existing product API and migration baselines.
- Browser E2E tests for catalog → run → detail → compare → artifact download using a small safe dataset; long benchmark runs should not be required in every PR.
- Performance validation of the workbench must be separate from performance claims about the target database.
- Regression tests for fixed seed generation, plan capture, dataset row counts, index state, and reported metrics.

## 12. Definition of done for QueryForge v2

- [ ] Existing experiments remain reproducible and the current benchmark claims remain traceable.
- [ ] The web UI can browse prior evidence and run at least one predefined experiment safely.
- [ ] Run manifests capture all conditions needed to judge comparability.
- [ ] Baseline/candidate comparisons identify meaningful environment mismatches.
- [ ] Plans and raw measurements can be downloaded and inspected.
- [ ] No arbitrary SQL or shell execution is exposed.
- [ ] Run timeouts, failures, cancellation (if offered), and restart behavior are tested.
- [ ] Typecheck/build/unit/integration checks pass; a bounded browser E2E smoke test passes.
- [ ] README contains real screenshots and one concise reproducible result story.
- [ ] No performance claim is based solely on a UI-generated summary.

## 13. CV evidence to earn

A credible CV statement after release could describe a full-stack workbench for reproducible PostgreSQL experiments, with controlled k6 workloads, captured execution plans, immutable artifacts, and before/after comparisons. Retain the measured 63× p95 result only when the final version can reproduce it under stated conditions.

The strongest interview narrative is not “I built a UI for SQL.” It is: “I designed a repeatable experiment, made the run conditions explicit, prevented invalid comparisons, observed the planner's response, measured the improvement, and documented the cost of the chosen index.”
