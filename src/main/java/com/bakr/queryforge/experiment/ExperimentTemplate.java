package com.bakr.queryforge.experiment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of a registered experiment template.
 */
public record ExperimentTemplate(
        String id,
        String title,
        String purpose,
        String hypothesis,
        List<ParameterField> parameters,
        String estimatedDuration,
        boolean destructive,
        List<String> outputs,
        String reportPath,
        List<String> planPaths) {

    public record ParameterField(String name,
                                 String type,
                                 String unit,
                                 Object min,
                                 Object max,
                                 List<String> allowed) {
    }
}
