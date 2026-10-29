package com.bakr.queryforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CompareRequest(
        @NotBlank(message = "leftRunId is required")
        String leftRunId,

        @NotBlank(message = "rightRunId is required")
        String rightRunId,

        String createdBy
) {
}