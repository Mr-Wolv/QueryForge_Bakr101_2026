package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stored comparison artifact linking two runs with a verdict, generated report,
 * and the measured metric deltas.
 */
public record ComparisonReport(
        String id,
        String leftRunId,
        String rightRunId,
        CompatibilityVerdict verdict,
        Map<String, MetricDelta> metricsDelta,
        String format,            // "markdown" | "html"
        String content,           // rendered report body
        List<RunArtifact> artifacts,
        Instant createdAt,
        String createdBy
) {}
