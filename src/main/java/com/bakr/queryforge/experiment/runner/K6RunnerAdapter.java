package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.experiment.ExperimentTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs k6 via the local binary or PATH-resolved k6.
 *
 * <p>Invocation shape is fixed in code. Client parameters become validated
 * environment variables only; no shell fragments are passed. Scripts and the
 * k6 binary are resolved to absolute paths because the process working
 * directory is the run directory, not the repository root.
 */
@Component
public class K6RunnerAdapter implements RunnerAdapter {

    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final String K6_BIN = System.getenv("K6_BIN");
    private static final String K6_PATH = System.getenv("K6_PATH");
    public static final String SUMMARY_FILE = "k6-summary.json";

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
            throws IOException {
        String k6Bin = resolveK6Binary();
        Path script = resolveScript(template.id());
        if (script == null || !Files.isRegularFile(script)) {
            throw new IOException("no k6 script registered for template " + template.id());
        }
        Files.createDirectories(workDir);

        List<String> cmd = new ArrayList<>();
        cmd.add(k6Bin);
        cmd.add("run");
        cmd.add("--out");
        cmd.add("json=" + workDir.resolve("raw-k6.json").toAbsolutePath());
        // Deterministic summary location owned by the run directory.
        cmd.add("-e");
        cmd.add("SUMMARY_PATH=" + workDir.resolve(SUMMARY_FILE).toAbsolutePath());
        for (Map.Entry<String, String> e : parameters.entrySet()) {
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        cmd.add(script.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        Process process = pb.start();

        // Bounded capture of stdout/stderr to avoid unbounded memory growth (NFR-003).
        final long cap = 1_048_576L; // 1 MiB per stream
        Thread stdoutDrainer = new Thread(
                () -> drain(process.getInputStream(), workDir.resolve("stdout.log"), cap), "k6-stdout-drain");
        Thread stderrDrainer = new Thread(
                () -> drain(process.getErrorStream(), workDir.resolve("stderr.log"), cap), "k6-stderr-drain");
        stdoutDrainer.setDaemon(true);
        stderrDrainer.setDaemon(true);
        stdoutDrainer.start();
        stderrDrainer.start();
        return process;
    }

    @Override
    public Path locateSummary(Path workDir, Map<String, String> parameters) {
        Path direct = workDir.resolve(SUMMARY_FILE);
        if (Files.isRegularFile(direct)) return direct;

        String out = parameters.getOrDefault("OUT", "k6-run");
        Path nested = workDir.resolve("benchmark").resolve("results").resolve(out + ".json");
        if (Files.isRegularFile(nested)) return nested;
        Path legacy = workDir.resolve(out + ".json");
        if (Files.isRegularFile(legacy)) return legacy;
        return null;
    }

    private String resolveK6Binary() throws IOException {
        if (K6_BIN != null && !K6_BIN.isBlank()) return absoluteIfPossible(K6_BIN);
        if (K6_PATH != null && !K6_PATH.isBlank()) return absoluteIfPossible(K6_PATH);
        String executable = isWindows() ? "k6.exe" : "k6";
        Path bundled = REPO_ROOT.resolve("tools").resolve("k6").resolve(executable);
        if (Files.isRegularFile(bundled)) return bundled.toString();
        // Fall back to PATH-resolved k6
        return "k6";
    }

    private static String absoluteIfPossible(String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path.toString() : REPO_ROOT.resolve(path).normalize().toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void drain(java.io.InputStream in, Path out, long cap) {
        byte[] buf = new byte[8192];
        long written = 0;
        try (java.io.OutputStream os = Files.newOutputStream(out,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            int n;
            while ((n = in.read(buf)) != -1 && written < cap) {
                int toWrite = (int) Math.min(n, cap - written);
                os.write(buf, 0, toWrite);
                written += toWrite;
            }
            if (written >= cap) {
                os.write("\n[output truncated at 1 MiB]\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // process output is best-effort evidence
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    Path resolveScript(String templateId) {
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
}
