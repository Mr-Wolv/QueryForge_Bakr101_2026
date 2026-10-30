package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable run manifest written atomically with checksums.
 * Contains all fields needed to judge comparability and reproducibility.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RunManifest(
        String runId,
        String templateId,
        String templateVersion,
        String state,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        String outcome,
        Map<String, String> parameters,
        Map<String, String> labels,
        Map<String, Object> environmentFingerprint,
        String migrationVersion,
        String indexState,
        Map<String, Object> resourceLimits,
        String appCommit,
        Map<String, Object> summaryMetrics,
        List<ArtifactEntry> artifacts,
        String checksumSha256
) {

    public record ArtifactEntry(
            String artifactId,
            String kind,
            String contentType,
            String location,
            String description,
            String checksumSha256
    ) {}
}
