# Comparison Report

| Field | Left | Right |
|-------|------|-------|
| Run ID | `[[${leftId}]]` | `[[${rightId}]]` |
| Template | [[${leftTemplate}]] | [[${rightTemplate}]] |
| State | [[${leftState}]] | [[${rightState}]] |
| Duration | [[${leftDuration}]]ms | [[${rightDuration}]]ms |

## Verdict

[# th:if="${compatible}"]
**COMPATIBLE** — runs can be compared.
[/]
[# th:unless="${compatible}"]
**INCOMPATIBLE** — runs cannot be meaningfully compared. Deltas below are shown for diagnosis, not as a controlled result.

### Blockers

[# th:each="blocker : ${blockers}"]
- [[${blocker}]]
[/]
[/]
[# th:if="${!#lists.isEmpty(warnings)}"]
### Warnings

[# th:each="warning : ${warnings}"]
- [[${warning}]]
[/]
[/]
[# th:if="${!#lists.isEmpty(differences)}"]
## Differences

[# th:each="difference : ${differences}"]
- **[[${difference.key}]]**: [[${difference.value}]]
[/]
[/]
[# th:if="${!#lists.isEmpty(deltas)}"]
## Metric Deltas

| Metric | Left | Right | Delta | Delta % |
|--------|------|-------|-------|---------|
[# th:each="delta : ${deltas}"]
| [[${delta.metric}]] | [[${delta.left}]] | [[${delta.right}]] | [[${delta.delta}]] | [[${delta.percent}]] |
[/]
[/]
## Reproduction

Both runs are reproducible from their stored manifests and artifacts.
