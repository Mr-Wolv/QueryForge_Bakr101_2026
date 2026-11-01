package com.bakr.queryforge.experiment;

/**
 * Explanation for a run that did not succeed.
 *
 * <p>The orchestrator already records an {@code outcome} on the run manifest and captures the
 * runner's stderr as an artifact, but neither was reachable through the API, so a FAILED run
 * rendered as a bare state badge with an empty log panel. This is a read-only projection of
 * evidence that already exists — it does not add a column or change the run document.
 *
 * @param runId      run these diagnostics belong to
 * @param state      terminal state of the run
 * @param outcome    manifest outcome, for example "failed with exit code 1"
 * @param stderrTail bounded tail of the captured runner stderr (empty when nothing was captured)
 */
public record RunDiagnostics(
        String runId,
        String state,
        String outcome,
        String stderrTail) {
}
