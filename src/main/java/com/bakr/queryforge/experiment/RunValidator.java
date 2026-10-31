package com.bakr.queryforge.experiment;

import com.bakr.queryforge.experiment.ExperimentTemplate.ParameterField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates run parameters and generates deterministic run IDs.
 *
 * <p>Extracted from {@link ExperimentOrchestrator} as part of MAI-001.
 */
class RunValidator {

    private static final Pattern SAFE_TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern DURATION = Pattern.compile("\\d{1,4}(s|m)?");

    Map<String, String> validateParameters(ExperimentTemplate template, Map<String, String> parameters) {
        Map<String, String> input = parameters == null ? Map.of() : parameters;
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Set<String> known = new LinkedHashSet<>();

        for (ParameterField field : template.parameters()) {
            known.add(field.name());
            String value = input.get(field.name());
            boolean optional = "OUT".equals(field.name());
            if (value == null || value.isBlank()) {
                if (!optional) errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                        .add("is required");
                continue;
            }
            if ("integer".equals(field.type())) {
                try {
                    int v = Integer.parseInt(value);
                    if (field.min() instanceof Integer min && v < min) {
                        errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                                .add("must be >= " + min);
                    }
                    if (field.max() instanceof Integer max && v > max) {
                        errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                                .add("must be <= " + max);
                    }
                } catch (NumberFormatException e) {
                    errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                            .add("must be an integer");
                }
            } else if ("duration".equals(field.type())) {
                if (!DURATION.matcher(value).matches()) {
                    errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                            .add("must be seconds (for example 60) or a duration such as 2m");
                } else {
                    long seconds = durationSeconds(value);
                    if (field.min() instanceof Integer min && seconds < min) {
                        errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                                .add("must be >= " + min + "s");
                    }
                    if (field.max() instanceof Integer max && seconds > max) {
                        errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                                .add("must be <= " + max + "s");
                    }
                }
            } else if ("enum".equals(field.type()) && field.allowed() != null
                    && !field.allowed().isEmpty() && !field.allowed().contains(value)) {
                errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                        .add("must be one of " + field.allowed());
            } else if ("string".equals(field.type()) && !SAFE_TAG.matcher(value).matches()) {
                errors.computeIfAbsent(field.name(), k -> new ArrayList<>())
                        .add("must match [A-Za-z0-9._-], 1-64 characters");
            }
        }

        for (String key : input.keySet()) {
            if (!known.contains(key)) {
                errors.computeIfAbsent(key, k -> new ArrayList<>()).add("is not a registered parameter");
            }
        }

        if (!errors.isEmpty()) throw new ParameterValidationException(errors);

        Map<String, String> normalized = new LinkedHashMap<>(input);
        if (!normalized.containsKey("OUT") || normalized.get("OUT").isBlank()) {
            normalized.put("OUT", "run-" + UUID.randomUUID().toString().substring(0, 8));
        }
        return Map.copyOf(normalized);
    }

    String generateRunId(String templateId, Map<String, String> parameters,
                         ExperimentRunService runService) {
        String out = parameters.getOrDefault("OUT", UUID.randomUUID().toString().substring(0, 8));
        if (!SAFE_TAG.matcher(out).matches()) {
            throw new ParameterValidationException(
                    Map.of("OUT", List.of("must match [A-Za-z0-9._-], 1-64 characters")));
        }
        String base = templateId.toLowerCase() + "-" + out + "-" + System.currentTimeMillis();
        String candidate = base;
        int suffix = 1;
        while (activeRunExists(candidate, runService)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean activeRunExists(String candidate, ExperimentRunService runService) {
        return runService.findById(candidate).isPresent();
    }

    static long durationSeconds(String value) {
        if (value.endsWith("m")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 60;
        }
        if (value.endsWith("s")) {
            return Long.parseLong(value.substring(0, value.length() - 1));
        }
        return Long.parseLong(value);
    }

    static long resolveTimeoutMs(ExperimentTemplate template, Map<String, String> parameters) {
        String duration = parameters == null ? null : parameters.get("DUR");
        if (duration != null && DURATION.matcher(duration).matches()) {
            long seconds = durationSeconds(duration);
            return Math.min(java.util.concurrent.TimeUnit.SECONDS.toMillis(seconds * 3 + 60),
                    java.util.concurrent.TimeUnit.MINUTES.toMillis(30));
        }
        String estimated = template.estimatedDuration();
        long minutes = 10;
        if (estimated != null) {
            String digits = estimated.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    minutes = Long.parseLong(digits);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Math.min(java.util.concurrent.TimeUnit.MINUTES.toMillis(Math.max(minutes * 3, 5)),
                java.util.concurrent.TimeUnit.MINUTES.toMillis(30));
    }
}
