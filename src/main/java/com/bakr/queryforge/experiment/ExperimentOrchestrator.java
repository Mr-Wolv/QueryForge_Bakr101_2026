package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.Process;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledFuture;
import com.bakr.queryforge.experiment.runner.RunnerAdapter;

/**
 * Phase 2 orchestrator: validates parameters, launches registered runner adapters,
 * and writes immutable run manifests. Supports cancel, timeout watchdog, and restart recovery.
 */
@Service
public class ExperimentOrchestrator {

    private static final Path RUNS_DIR = Path.of("benchmark", "runs");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    private final ExperimentTemplateService templates;
    private final ArtifactService artifactService;
    private final List<RunnerAdapter> adapters;
    private final Map<String, ExperimentRun> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();

    public ExperimentOrchestrator(ExperimentTemplateService templates, ArtifactService artifactService,
                                  List<RunnerAdapter> adapters) {
        this.templates = templates;
        this.artifactService = artifactService;
        this.adapters = adapters;
        performRestartRecovery();
    }

    public ExperimentRun createRun(String templateId, Map<String, String> parameters, String clientRequestId) {
        ExperimentTemplate template = templates.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("unknown template: " + templateId));
        validateParameters(template, parameters);

        String runId = generateRunId(templateId, parameters);
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            if (findByClientRequestId(clientRequestId).isPresent()) {
                throw new IllegalStateException("duplicate request id: " + clientRequestId);
            }
        }

        Map<String, String> labels = clientRequestId != null && !clientRequestId.isBlank()
                ? Map.of("clientRequestId", clientRequestId)
                : Map.of();
        ExperimentRun run = new ExperimentRun(
                runId, templateId, ExperimentRunState.QUEUED.name(),
                Instant.now(), 0, labels,
                com.bakr.queryforge.measurement.Metrics.empty(),
                List.of()
        );
        activeRuns.put(runId, run);
        return run;
    }

    public ExperimentRun run(String runId, ExperimentTemplate template, Map<String, String> parameters) throws Exception {
        ExperimentRun run = activeRuns.get(runId);
        if (run == null) throw new IllegalArgumentException("run not found: " + runId);

        run = new ExperimentRun(
                run.id(), run.templateId(), ExperimentRunState.RUNNING.name(),
                run.startedAt(), run.durationMs(), run.labels(),
                run.metrics(), run.artifacts()
        );
        activeRuns.put(runId, run);

        Path workDir = RUNS_DIR.resolve(runId);
        Files.createDirectories(workDir);

        RunnerAdapter adapter = adapters.stream()
                .filter(a -> a.name().equals("k6"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no runner adapter registered"));

        long timeoutMs = resolveTimeoutMs(template);
        long started = System.currentTimeMillis();

        AtomicReference<ExperimentRunState> terminalState = new AtomicReference<>(ExperimentRunState.SUCCEEDED);
        AtomicReference<String> outcome = new AtomicReference<>("completed");
        List<RunArtifact> artifacts = Collections.synchronizedList(new ArrayList<>());

        Process process = null;
        try {
            process = adapter.execute(template, parameters, workDir);
            runningProcesses.put(runId, process);

            final Process finalProcess = process;
            ScheduledFuture<?> watchdogTask = watchdog.schedule(() -> {
                if (finalProcess.isAlive()) {
                    terminalState.set(ExperimentRunState.TIMED_OUT);
                    outcome.set("timed out after " + timeoutMs + "ms");
                    finalProcess.destroyForcibly();
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);

            int exit = process.waitFor();
            watchdogTask.cancel(false);

            runningProcesses.remove(runId);
            if (terminalState.get() == ExperimentRunState.TIMED_OUT) {
                // watchdog already set state
            } else if (exit == 0) {
                terminalState.set(ExperimentRunState.SUCCEEDED);
                outcome.set("completed");
            } else {
                terminalState.set(ExperimentRunState.FAILED);
                outcome.set("failed with exit code " + exit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                runningProcesses.remove(runId);
            }
            terminalState.set(ExperimentRunState.CANCELLED);
            outcome.set("interrupted/cancelled");
            throw e;
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                runningProcesses.remove(runId);
            }
            terminalState.set(ExperimentRunState.FAILED);
            outcome.set("failed: " + e.getMessage());
            throw e;
        } finally {
            runningProcesses.remove(runId);
            long dur = System.currentTimeMillis() - started;
            run = new ExperimentRun(run.id(), run.templateId(), terminalState.get().name(),
                    run.startedAt(), dur, run.labels(), run.metrics(), artifacts);
            activeRuns.put(runId, run);
            writeManifest(runId, run, outcome.get(), artifacts);
        }
        return run;
    }

    public boolean cancel(String runId) {
        ExperimentRun run = activeRuns.get(runId);
        if (run == null) return false;

        Process process = runningProcesses.get(runId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            runningProcesses.remove(runId);
        }

        run = new ExperimentRun(
                run.id(), run.templateId(), ExperimentRunState.CANCELLED.name(),
                run.startedAt(), run.durationMs(), run.labels(),
                run.metrics(), run.artifacts()
        );
        activeRuns.put(runId, run);
        writeManifest(runId, run, "cancelled by user", List.of());
        return true;
    }

    /**
     * On startup, mark any in-progress runs (RUNNING, QUEUED) as FAILED.
     */
    private void performRestartRecovery() {
        if (!Files.isDirectory(RUNS_DIR)) return;
        try (var stream = Files.list(RUNS_DIR)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            var node = MAPPER.readTree(path.toFile());
                            String state = node.path("state").asText("");
                            if ("RUNNING".equals(state) || "QUEUED".equals(state)) {
                                String runId = path.getFileName().toString().replaceAll("\\.json$", "");
                                long startedAt = node.path("startedAt").isMissingNode()
                                        ? Instant.now().toEpochMilli()
                                        : Instant.parse(node.path("startedAt").asText()).toEpochMilli();
                                ExperimentRun recovered = new ExperimentRun(
                                        runId,
                                        node.path("templateId").asText("UNKNOWN"),
                                        ExperimentRunState.FAILED.name(),
                                        Instant.ofEpochMilli(startedAt),
                                        node.path("durationMs").asLong(0),
                                        Map.of(),
                                        com.bakr.queryforge.measurement.Metrics.empty(),
                                        List.of()
                                );
                                activeRuns.put(runId, recovered);
                                writeManifest(runId, recovered, "recovered: interrupted run marked FAILED on restart", List.of());
                            }
                        } catch (Exception e) {
                            // skip corrupt manifests
                        }
                    });
        } catch (IOException e) {
            // no recovery possible
        }
    }

    public Optional<ExperimentRun> findById(String id) {
        return Optional.ofNullable(activeRuns.get(id));
    }

    public Optional<ExperimentRun> findByClientRequestId(String clientRequestId) {
        return activeRuns.values().stream()
                .filter(r -> clientRequestId.equals(r.labels().get("clientRequestId")))
                .findFirst();
    }

    public List<ExperimentRun> loadAll() {
        if (!Files.isDirectory(RUNS_DIR)) return List.of();
        try (var stream = Files.list(RUNS_DIR)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .map(this::parseManifest)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(r -> r.startedAt().toEpochMilli()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void validateParameters(ExperimentTemplate template, Map<String, String> parameters) {
        for (ExperimentTemplate.ParameterField field : template.parameters()) {
            String value = parameters.get(field.name());
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing parameter: " + field.name());
            }
            if ("integer".equals(field.type())) {
                int v = Integer.parseInt(value);
                if (field.min() instanceof Integer min && v < min) {
                    throw new IllegalArgumentException(field.name() + " below min " + min);
                }
                if (field.max() instanceof Integer max && v > max) {
                    throw new IllegalArgumentException(field.name() + " above max " + max);
                }
            }
            if ("enum".equals(field.type()) && field.allowed() != null && !field.allowed().isEmpty()) {
                if (!field.allowed().contains(value)) {
                    throw new IllegalArgumentException(field.name() + " must be one of " + field.allowed());
                }
            }
        }
    }

    private String generateRunId(String templateId, Map<String, String> parameters) {
        String out = parameters.getOrDefault("OUT", UUID.randomUUID().toString().substring(0, 8));
        return templateId.toLowerCase() + "-" + out + "-" + System.currentTimeMillis();
    }

    private void writeManifest(String runId, ExperimentRun run, String outcome, List<RunArtifact> artifacts) {
            try {
                Files.createDirectories(RUNS_DIR);
            
                // Build artifact entries with per-artifact checksums
                List<RunManifest.ArtifactEntry> artifactEntries = artifacts.stream().map(a -> {
                    String artifactChecksum = "unknown";
                    try {
                        Path artifactPath = Path.of(a.location());
                        if (Files.isRegularFile(artifactPath)) {
                            byte[] artifactBytes = Files.readAllBytes(artifactPath);
                            artifactChecksum = sha256(artifactBytes);
                        }
                    } catch (Exception ignored) {}
                    return new RunManifest.ArtifactEntry(
                            a.kind(), a.contentType(), a.location(), a.description(), artifactChecksum);
                }).toList();

                // Get environment fingerprint from readiness endpoint data
                Map<String, Object> envFingerprint = Map.of(
                        "appCommit", envOrDefault("GIT_COMMIT", "unknown"),
                        "javaVersion", System.getProperty("java.version"),
                        "postgresVersion", "unknown", // would need DB connection to get actual version
                        "checkedAt", Instant.now().toString()
                );

                RunManifest manifest = new RunManifest(
                        run.id(),
                        run.templateId(),
                        "1.0", // templateVersion
                        run.state(),
                        run.startedAt(),
                        run.state().matches("SUCCEEDED|FAILED|CANCELLED|TIMED_OUT") ? Instant.now() : null,
                        run.durationMs(),
                        outcome,
                        run.labels(), // parameters stored in labels
                        envFingerprint,
                        "V1", // migrationVersion
                        "baseline", // indexState
                        Map.of(
                                "cpuCores", Runtime.getRuntime().availableProcessors(),
                                "memoryMb", Runtime.getRuntime().maxMemory() / 1024 / 1024
                        ),
                        envOrDefault("GIT_COMMIT", "unknown"),
                        artifactEntries,
                        null // checksum will be computed below
                );

                // Compute manifest checksum (without the checksum field itself)
                String manifestJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
                String checksum = sha256(manifestJson.getBytes());
            
                // Create final manifest with checksum
                RunManifest finalManifest = new RunManifest(
                        manifest.runId(), manifest.templateId(), manifest.templateVersion(),
                        manifest.state(), manifest.startedAt(), manifest.endedAt(),
                        manifest.durationMs(), manifest.outcome(), manifest.parameters(),
                        manifest.environmentFingerprint(), manifest.migrationVersion(),
                        manifest.indexState(), manifest.resourceLimits(), manifest.appCommit(),
                        manifest.artifacts(), checksum
                );

                String finalJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(finalManifest);
                Path path = RUNS_DIR.resolve(runId + ".json");
            
                // Atomic write: CREATE_NEW fails if file exists
                Files.writeString(path, finalJson, StandardOpenOption.CREATE_NEW);
            
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // Manifest already exists - this should not happen for new runs
                throw new IllegalStateException("Manifest already exists for run: " + runId, e);
            } catch (Exception e) {
                // Manifest write failure should not corrupt the run record
                // Log the error but don't throw
            }
        }

    private ExperimentRun parseManifest(Path path) {
        try {
            var node = MAPPER.readTree(path.toFile());
            String id = path.getFileName().toString().replaceAll("\\.json$", "");
            String templateId = node.path("templateId").asText("UNKNOWN");
            String state = node.path("state").asText("UNKNOWN");
            long startedAt = node.path("startedAt").isMissingNode() ? 0
                    : Instant.parse(node.path("startedAt").asText()).toEpochMilli();
            long dur = node.path("durationMs").asLong(0);
            return new ExperimentRun(id, templateId, state,
                    Instant.ofEpochMilli(startedAt), dur, Map.of(),
                    com.bakr.queryforge.measurement.Metrics.empty(), List.of());
        } catch (Exception e) {
            return null;
        }
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    private static long resolveTimeoutMs(ExperimentTemplate template) {
        String est = template.estimatedDuration();
        long mins = 10;
        if (est != null) {
            String digits = est.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    mins = Long.parseLong(digits);
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.min(TimeUnit.MINUTES.toMillis(Math.max(mins * 3, 5)), TimeUnit.MINUTES.toMillis(30));
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
