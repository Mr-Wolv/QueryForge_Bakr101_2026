# 01 — Vision and Boundaries

## 1.1 Product Statement

QueryForge is a **local-first PostgreSQL performance workbench**. It runs controlled, predefined experiments against a known dataset, captures the evidence, and makes that evidence inspectable, comparable, and reproducible. It does not guess, and it does not auto-optimize production systems.

## 1.2 Product Principle

> **Measure first. Optimize from evidence. Preserve reproducibility. Never claim a result the artifacts cannot support.**

## 1.3 Intended Users

| User | Job to be Done |
|------|----------------|
| Developer diagnosing a slow query | Reproduce the workload, understand the plan, compare a candidate change, explain why it helped or failed. |
| Reviewer / Interviewer | Understand the experiment without a long setup session: view the baseline, see the measured delta, inspect a representative plan, download evidence. |
| Maintainer / Project owner | Experiments are repeatable, bounded, inspectable, safe to run, and straightforward to extend without breaking prior evidence. |

## 1.4 Strategic Boundaries

### In Scope
- Angular/TypeScript evidence browser served alongside the existing Spring Boot API.
- Experiment catalog with scenario description, workload parameters, expected outputs, safety classification, and reproducibility instructions.
- Run history with state, timestamps, parameters, environment fingerprint, progress, measurements, and artifact links.
- Baseline-versus-candidate comparisons with compatibility verdicts.
- Query-plan inspection with readable rendering while preserving raw artifacts.
- Latency/throughput/error graphs with explicit environment labels.
- A safe, allow-listed way to run existing experiments.
- Export of experiment reports and downloadable raw artifacts.
- Optional, explainable optimization hints (gated behind core stability).

### Out of Scope
- PostgreSQL competitor, SQL engine, storage engine, or general DBMS.
- Arbitrary SQL execution through a public API.
- Arbitrary command execution or shell argument injection via web forms.
- Automatic production database changes.
- General-purpose APM, cloud benchmarking marketplace, team collaboration, billing, or multi-tenant SaaS.
- Redis, Kafka, Kubernetes, or other infrastructure that would confound database experiments.
- Replacing the PostgreSQL planner with hand-authored optimizer rules.

## 1.5 Success Criteria

The renovation is successful when:
1. A reviewer can reproduce the headline 63× result from a fresh checkout using documented commands.
2. The Angular UI passes a 9.5/10 editorial review for typography, spacing, dark mode, and chart quality.
3. The backend passes a 9.5/10 engineering review for safety, observability, and API maturity.
4. The test suite provides evidence for every acceptance criterion in this plan.
5. No scope beyond the documented boundaries is implemented without a written change request.
