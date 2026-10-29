package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable run manifest stored after a benchmark completes.
 */
public record RunManifest(
        String runId,
        String templateId,
        ExperimentRunState state,
        String clientRequestId,
        Map<String, String> parameters,
        String environmentFingerprint,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        String outcome,
        List<RunArtifact> artifacts,
        String checksumSha256,
        String migrationVersion,
        String indexState,
        Map<String, Object> resourceLimits,
        String appCommit
) {
}
