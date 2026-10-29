package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Stored comparison artifact linking two runs with a verdict and generated report.
 */
public record ComparisonReport(
        String id,
        String leftRunId,
        String rightRunId,
        CompatibilityVerdict verdict,
        String format,            // "markdown" | "html"
        String content,           // rendered report body
        List<RunArtifact> artifacts,
        Instant createdAt,
        String createdBy
) {}
