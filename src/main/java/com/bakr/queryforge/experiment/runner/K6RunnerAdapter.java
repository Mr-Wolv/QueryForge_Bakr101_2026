package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.experiment.ExperimentTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.ProcessBuilder;
import java.lang.RuntimeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs k6 via the local binary or PATH-resolved k6.
 *
 * <p>Invocation shape is fixed in code. Client parameters become validated
 * environment variables only; no shell fragments are passed.
 */
@Component
public class K6RunnerAdapter implements RunnerAdapter {

    private static final String K6_BIN = System.getenv("K6_BIN");
    private static final String K6_PATH = System.getenv("K6_PATH");

    @Override
    public String name() {
        return "k6";
    }

    @Override
    public String description() {
        return "k6 constant-arrival-rate workload";
    }

    @Override
    public Process execute(ExperimentTemplate template, Map<String, String> parameters, Path workDir)
            throws IOException, InterruptedException {
        String k6Bin = resolveK6Binary();
        Path script = resolveScript(template.id());
        if (script == null || !Files.isRegularFile(script)) {
            throw new IOException("no k6 script registered for template " + template.id());
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(k6Bin);
        cmd.add("run");
        cmd.add("--out");
        cmd.add("json=" + workDir.resolve("raw-k6.json").toString());
        for (Map.Entry<String, String> e : parameters.entrySet()) {
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        cmd.add(script.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        return process;
    }

    /** Return the summary path after the process has completed (caller waits first). */
    public Path locateSummary(Path workDir, Map<String, String> parameters) {
        // k6 common.js writes to benchmark/results/<OUT>.json when OUT is set.
        String out = parameters.getOrDefault("OUT", "k6-run");
        Path p = workDir.resolve(out + ".json");
        return Files.isRegularFile(p) ? p : null;
    }

    private String resolveK6Binary() throws IOException {
        if (K6_BIN != null && !K6_BIN.isBlank()) return K6_BIN;
        if (K6_PATH != null && !K6_PATH.isBlank()) return K6_PATH;
        Path default_ = Path.of("tools", "k6", "k6.exe");
        if (Files.isRegularFile(default_)) return default_.toString();
        // Fall back to PATH-resolved k6
        return "k6";
    }

    Path resolveScript(String templateId) {
        return switch (templateId) {
            case "QF-001" -> Path.of("benchmark", "k6", "search.js");
            case "QF-002" -> Path.of("benchmark", "k6", "pagination.js");
            case "QF-003" -> Path.of("benchmark", "k6", "concurrency.js");
            case "QF-000" -> Path.of("benchmark", "k6", "point.js");
            default -> null;
        };
    }
}
