package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.config.InternalRunnerToken;
import com.bakr.queryforge.experiment.ExperimentTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class K6CommandGenerator {

    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final String K6_BIN = System.getenv("K6_BIN");
    private static final String K6_PATH = System.getenv("K6_PATH");

    public List<String> generate(ExperimentTemplate template, Map<String, String> parameters,
                                 Path workDir, String baseUrl, InternalRunnerToken runnerToken)
            throws IOException {
        String binary = resolveK6Binary();
        Path script = resolveScript(template.id());
        if (script == null || !Files.isRegularFile(script)) {
            throw new IOException("no k6 script registered for template " + template.id());
        }

        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add("run");
        command.add("--out");
        command.add("json=" + workDir.resolve("raw-k6.json").toAbsolutePath());
        command.add("-e");
        command.add("SUMMARY_PATH=" + workDir.resolve(K6RunnerAdapter.SUMMARY_FILE).toAbsolutePath());
        command.add("-e");
        command.add("BASE_URL=" + baseUrl);
        command.add("-e");
        command.add("QF_INTERNAL_TOKEN=" + runnerToken.value());
        for (Map.Entry<String, String> entry : k6Environment(template, parameters).entrySet()) {
            command.add("-e");
            command.add(entry.getKey() + "=" + entry.getValue());
        }
        command.add(script.toString());
        return List.copyOf(command);
    }

    public String resolveK6Binary() throws IOException {
        if (K6_BIN != null && !K6_BIN.isBlank()) return absoluteIfPossible(K6_BIN);
        if (K6_PATH != null && !K6_PATH.isBlank()) return absoluteIfPossible(K6_PATH);
        String executable = isWindows() ? "k6.exe" : "k6";
        Path bundled = REPO_ROOT.resolve("tools").resolve("k6").resolve(executable);
        return Files.isRegularFile(bundled) ? bundled.toString() : "k6";
    }

    public Path resolveScript(String templateId) {
        if (templateId == null) return null;
        String file = switch (templateId) {
            case "QF-001" -> "search.js";
            case "QF-002" -> "pagination.js";
            case "QF-003" -> "concurrency.js";
            case "QF-000" -> "point.js";
            case "QF-004" -> "index-ordering.js";
            default -> null;
        };
        return file == null ? null : REPO_ROOT.resolve("benchmark").resolve("k6").resolve(file).normalize();
    }

    public static Map<String, String> k6Environment(ExperimentTemplate template,
                                                    Map<String, String> parameters) {
        Map<String, String> fields = new java.util.HashMap<>();
        if (template != null && template.parameters() != null) {
            for (ExperimentTemplate.ParameterField field : template.parameters()) {
                fields.put(field.name(), field.type());
            }
        }
        Map<String, String> environment = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String value = entry.getValue();
            if ("duration".equals(fields.get(entry.getKey()))
                    && value != null && value.matches("\\d+")) {
                value += "s";
            }
            environment.put(entry.getKey(), value);
        }
        return environment;
    }

    private static String absoluteIfPossible(String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path.toString() : REPO_ROOT.resolve(path).normalize().toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
