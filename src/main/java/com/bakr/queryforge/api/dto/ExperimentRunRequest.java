package com.bakr.queryforge.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ExperimentRunRequest(
        @NotBlank(message = "templateId is required")
        String templateId,

        @NotNull(message = "parameters are required")
        @Valid
        @Size(min = 1, message = "at least one parameter is required")
        Map<String, String> parameters,

        String clientRequestId
) {
}