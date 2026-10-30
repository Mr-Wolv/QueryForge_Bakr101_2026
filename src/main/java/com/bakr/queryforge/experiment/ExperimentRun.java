package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable run record loaded from stored artifacts.
 */
public record ExperimentRun(
        String id,
        String templateId,
        String state,
        Instant startedAt,
        long durationMs,
        Map<String, String> labels,
        Map<String, String> parameters,
        com.bakr.queryforge.measurement.Metrics metrics,
        List<RunArtifact> artifacts,
        String appCommit) {
}
