package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lightweight compatibility verdict for a pair of runs.
 */
public record CompatibilityVerdict(
        String leftRunId,
        String rightRunId,
        boolean compatible,
        List<String> blockers,
        List<String> warnings,
        Map<String, String> diff
) {}
