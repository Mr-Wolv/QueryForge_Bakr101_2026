package com.bakr.queryforge.experiment;

import java.util.Map;

/**
 * Request body for creating a new experiment run.
 */
public record ExperimentRunRequest(
        String templateId,
        Map<String, String> parameters,
        String clientRequestId
) {
}
