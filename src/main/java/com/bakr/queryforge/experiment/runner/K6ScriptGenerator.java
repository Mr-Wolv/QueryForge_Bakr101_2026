package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.experiment.ExperimentTemplate;
import java.nio.file.Path;
import java.util.Map;

public final class K6ScriptGenerator {
    private K6ScriptGenerator() {}

    public static Path resolveScript(String templateId) {
        if (templateId == null) return null;
        String file = switch (templateId) {
            case "QF-001" -> "search.js";
            case "QF-002" -> "pagination.js";
            case "QF-003" -> "concurrency.js";
            case "QF-000" -> "point.js";
            case "QF-004" -> "index-ordering.js";
            default -> null;
        };
        if (file == null) return null;
        return java.nio.file.Path.of("").toAbsolutePath().normalize()
                .resolve("benchmark").resolve("k6").resolve(file).normalize();
    }

    public static Map<String, String> k6Environment(ExperimentTemplate template, Map<String, String> parameters) {
        return K6RunnerAdapter.k6Environment(template, parameters);
    }
}
