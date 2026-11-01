package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import com.bakr.queryforge.experiment.runner.K6RunnerAdapter;
import com.bakr.queryforge.experiment.runner.RunnerAdapter;
import com.bakr.queryforge.measurement.Metrics;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Phase 2 orchestrator: validates parameters, launches registered runner adapters,
 * and writes immutable run manifests. Supports cancel, timeout watchdog, live log
 * events, and restart recovery.
 */
@Service
public class ExperimentOrchestrator {

    private static final Pattern SAFE_TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern DURATION = Pattern.compile("\\d{1,4}(s|m)?");
    private static final Set<String> TERMINAL_STATES = Set.of(
            ExperimentRunState.SUCCEEDED.name(),
            ExperimentRunState.FAILED.name(),
            ExperimentRunState.CANCELLED.name(),
            ExperimentRunState.TIMED_OUT.name());

    private final ExperimentTemplateService templates;
    private final ManifestStore manifests;
    private final EnvironmentFingerprintService fingerprints;
    private final RunEventPublisher events;
    private final List<RunnerAdapter> adapters;

    private final Map<String, ExperimentRun> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, RunManifest> storedManifests = new ConcurrentHashMap<>();
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Set<String> cancelRequested = ConcurrentHashMap.newKeySet();
    private final Set<String> startingRuns = ConcurrentHashMap.newKeySet();
    private final RunPersistenceService persistence;
    private final RunScheduler scheduler;
    private final ProcessExecutor processExecutor;
    private final ManifestWriter manifestWriter;

    public ExperimentOrchestrator(ExperimentTemplateService templates,
                                  ManifestStore manifests,
                                  EnvironmentFingerprintService fingerprints,
                                  RunEventBus events,
                                  List<RunnerAdapter> adapters) {
        this(templates, manifests, fingerprints, events, adapters, null);
    }

    public ExperimentOrchestrator(ExperimentTemplateService templates,
                                  ManifestStore manifests,
                                  EnvironmentFingerprintService fingerprints,
                                  RunEventBus events,
                                  List<RunnerAdapter> adapters,
                                  RunPersistenceService persistence) {
        this(templates, manifests, fingerprints, events, adapters, persistence,
                null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExperimentOrchestrator(ExperimentTemplateService templates,
                                  ManifestStore manifests,
                                  EnvironmentFingerprintService fingerprints,
                                  RunEventBus eventBus,
                                  List<RunnerAdapter> adapters,
                                  RunPersistenceService persistence,
                                  RunScheduler scheduler,
                                  ProcessExecutor processExecutor,
                                  RunEventPublisher events,
                                  ManifestWriter manifestWriter) {
        this.templates = templates;
        this.manifests = manifests;
        this.fingerprints = fingerprints;
        this.events = events == null ? new RunEventPublisher(eventBus) : events;
        this.adapters = adapters;
        this.persistence = persistence;
        this.scheduler = scheduler == null ? new RunScheduler() : scheduler;
        this.processExecutor = processExecutor == null
                ? new ProcessExecutor(this.scheduler, this.events) : processExecutor;
        this.manifestWriter = manifestWriter == null
                ? new ManifestWriter(manifests, fingerprints) : manifestWriter;
        performRestartRecovery();
    }

    public synchronized ExperimentRun createRun(String templateId,
                                                Map<String, String> parameters,
                                                String clientRequestId) {
        ExperimentTemplate template = templates.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("unknown template: " + templateId));
        Map<String, String> validated = validateParameters(template, parameters);

        if (clientRequestId != null && !clientRequestId.isBlank()) {
            if (findByClientRequestId(clientRequestId).isPresent()) {
                throw new DuplicateRequestException(clientRequestId);
            }
        }

        Map<String, String> labels = clientRequestId != null && !clientRequestId.isBlank()
                ? Map.of("clientRequestId", clientRequestId)
                : Map.of();
        String runId = generateRunId(templateId, validated);
        ExperimentRun run = new ExperimentRun(
                runId, templateId, ExperimentRunState.QUEUED.name(),
                Instant.now(), 0, labels, validated,
                Metrics.empty(), List.of(), fingerprints.appCommit()
        );
        activeRuns.put(runId, run);
        events.state(runId, run.state());
        if (persistence != null) persistence.save(run);
        return run;
    }

    /**
     * Begin execution on the runner pool and return immediately with RUNNING.
     * The endpoint stays responsive while the workload executes.
     */
    public ExperimentRun startAsync(String runId) {
        ExperimentRun run = findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        if (!ExperimentRunState.QUEUED.name().equals(run.state())) {
            throw new RunConflictException("run is not queued: " + run.state());
        }
        if (!startingRuns.add(runId)) {
            throw new RunConflictException("run is already starting");
        }
        ExperimentTemplate template = templates.findById(run.templateId())
                .orElseThrow(() -> new IllegalStateException("template not found: " + run.templateId()));

        ExperimentRun running = new ExperimentRun(
                run.id(), run.templateId(), ExperimentRunState.RUNNING.name(),
                run.startedAt(), 0, run.labels(), run.parameters(),
                run.metrics(), run.artifacts(), run.appCommit());
        activeRuns.put(runId, running);
        events.state(runId, running.state());
        if (persistence != null) persistence.save(running);

        scheduler.submit(() -> {
            try {
                execute(runId, template, run.parameters());
            } catch (RuntimeException e) {
                ExperimentRun failed = new ExperimentRun(
                        run.id(), run.templateId(), ExperimentRunState.FAILED.name(),
                        run.startedAt(), 0, run.labels(), run.parameters(),
                        Metrics.empty(), List.of(), run.appCommit());
                activeRuns.put(runId, failed);
                if (persistence != null) persistence.save(failed);
                events.error(runId, "runner failed to start: " + e.getMessage());
                events.state(runId, failed.state());
                events.complete(runId);
            } finally {
                startingRuns.remove(runId);
            }
        });
        return running;
    }

    /** Synchronous execution used by tests and trusted internal callers. */
    public ExperimentRun run(String runId, ExperimentTemplate template, Map<String, String> parameters) {
        return execute(runId, template, parameters);
    }

    private ExperimentRun execute(String runId, ExperimentTemplate template, Map<String, String> parameters) {
        ExperimentRun current = activeRuns.getOrDefault(runId,
                findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found: " + runId)));
        Instant startedAt = current.startedAt() == null ? Instant.now() : current.startedAt();
        ExperimentRun running = new ExperimentRun(
                current.id(), current.templateId(), ExperimentRunState.RUNNING.name(),
                startedAt, 0, current.labels(), current.parameters(),
                current.metrics(), current.artifacts(), current.appCommit());
        activeRuns.put(runId, running);
        events.state(runId, running.state());

        RunnerAdapter adapter = adapterOrNull();
        EnvironmentFingerprintService.Snapshot fingerprint =
                fingerprints.capture(template.id(), parameters, adapter);
        Path workDir = manifests.runDir(runId);
        long started = System.currentTimeMillis();
        AtomicReference<ExperimentRunState> terminalState = new AtomicReference<>(ExperimentRunState.SUCCEEDED);
        AtomicReference<String> outcome = new AtomicReference<>("completed");
        Process process = null;
        ProcessExecutor.ManagedProcess execution = null;
        AtomicReference<ExperimentRun> result = new AtomicReference<>();

        try {
            Files.createDirectories(workDir);
            if (adapter == null) {
                throw new IllegalStateException("no runner adapter registered");
            }

            execution = processExecutor.start(adapter, template, parameters, workDir, runId);
            process = execution.process();
            runningProcesses.put(runId, process);

            final Process finalProcess = process;
            int exit = processExecutor.await(execution, resolveTimeoutMs(template, parameters), () -> {
                if (finalProcess.isAlive()) {
                    terminalState.set(ExperimentRunState.TIMED_OUT);
                    outcome.set("timed out after " + resolveTimeoutMs(template, parameters) + "ms");
                }
            });

            if (cancelRequested.contains(runId)) {
                terminalState.set(ExperimentRunState.CANCELLED);
                outcome.set("cancelled by user");
            } else if (terminalState.get() == ExperimentRunState.TIMED_OUT) {
                outcome.set("timed out after " + resolveTimeoutMs(template, parameters) + "ms");
            } else if (exit == 0) {
                terminalState.set(ExperimentRunState.SUCCEEDED);
                outcome.set("completed");
            } else {
                terminalState.set(ExperimentRunState.FAILED);
                outcome.set("failed with exit code " + exit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null && process.isAlive()) process.destroyForcibly();
            terminalState.set(ExperimentRunState.CANCELLED);
            outcome.set("interrupted/cancelled");
        } catch (Exception e) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            terminalState.set(ExperimentRunState.FAILED);
            outcome.set("failed: " + e.getMessage());
        } finally {
            runningProcesses.remove(runId);
            processExecutor.close(execution);

            List<RunArtifact> artifacts = collectArtifacts(runId, workDir, parameters, adapter);
            Metrics metrics = readMetrics(workDir, parameters, adapter);
            ExperimentRun finished = new ExperimentRun(
                    running.id(), running.templateId(), terminalState.get().name(),
                    startedAt, Math.max(0, System.currentTimeMillis() - started),
                    running.labels(), running.parameters(), metrics, artifacts,
                    fingerprint.appCommit());
            result.set(finished);
            activeRuns.put(runId, finished);
            if (persistence != null) persistence.save(finished);

            boolean manifestWritten = true;
            try {
                writeManifest(finished, outcome.get(), fingerprint, Instant.now());
            } catch (Exception e) {
                manifestWritten = false;
                events.error(runId, e.getMessage());
            }

            if (manifestWritten) {
                cleanupActiveRuns();
            }
            for (RunArtifact artifact : artifacts) {
                events.artifact(runId, artifact.artifactId());
            }
            events.state(runId, finished.state());
            if (ExperimentRunState.FAILED.name().equals(finished.state())
                    || ExperimentRunState.TIMED_OUT.name().equals(finished.state())) {
                events.error(runId, outcome.get());
            } else if (!manifestWritten) {
                events.error(runId, "manifest write failed; run is not reproducible");
            }
            events.complete(runId);
        }
        return result.get() == null ? running : result.get();
    }

    public boolean cancel(String runId) {
        ExperimentRun run = findById(runId).orElse(null);
        if (run == null || TERMINAL_STATES.contains(run.state())) return false;

        cancelRequested.add(runId);
        Process process = runningProcesses.get(runId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            ExperimentRun cancelling = new ExperimentRun(
                    run.id(), run.templateId(), "CANCELLING",
                    run.startedAt(), run.durationMs(), run.labels(), run.parameters(),
                    run.metrics(), run.artifacts(), run.appCommit());
            activeRuns.put(runId, cancelling);
            events.state(runId, cancelling.state());
            if (persistence != null) persistence.save(cancelling);
            return true;
        }

        // Queued run (or a runner that already exited): finalize immediately.
        ExperimentRun cancelled = new ExperimentRun(
                run.id(), run.templateId(), ExperimentRunState.CANCELLED.name(),
                run.startedAt(), run.durationMs(), run.labels(), run.parameters(),
                run.metrics(), run.artifacts(), run.appCommit());
        activeRuns.put(runId, cancelled);
        writeManifest(cancelled, "cancelled by user", fingerprints.capture(), Instant.now());
        events.state(runId, cancelled.state());
        events.complete(runId);
        if (persistence != null) persistence.save(cancelled);
        return true;
    }

    /**
     * On startup, mark any in-progress runs (RUNNING, QUEUED, CANCELLING) as FAILED.
     */
    private void performRestartRecovery() {
        List<RunManifest> stored = manifests.readAll();
        for (RunManifest manifest : stored) {
            storedManifests.put(manifest.runId(), manifest);
        }
        for (RunManifest manifest : stored) {
            if ("RUNNING".equals(manifest.state())
                    || "QUEUED".equals(manifest.state())
                    || "CANCELLING".equals(manifest.state())) {
                ExperimentRun recovered = new ExperimentRun(
                        manifest.runId(), manifest.templateId(), ExperimentRunState.FAILED.name(),
                        manifest.startedAt(), manifest.durationMs(), manifest.labels(),
                        manifest.parameters(), Metrics.fromSummaryMap(manifest.summaryMetrics()),
                        artifactsOf(manifest), manifest.appCommit());
                try {
                    writeManifest(recovered,
                            "recovered: interrupted run marked FAILED on restart",
                            fingerprints.capture(), Instant.now());
                } catch (Exception ignored) {
                    // recovery is best-effort; the original evidence remains on disk
                }
            }
        }
    }

    public Optional<ExperimentRun> findById(String id) {
        ExperimentRun active = activeRuns.get(id);
        if (active != null) return Optional.of(active);
        return findStoredManifest(id).map(this::toRun);
    }

    /** Full stored manifest, used by comparison/export to read the environment fingerprint. */
    public Optional<RunManifest> manifest(String runId) {
        return findStoredManifest(runId);
    }

    public Optional<ExperimentRun> findByClientRequestId(String clientRequestId) {
        Optional<ExperimentRun> active = activeRuns.values().stream()
                .filter(r -> clientRequestId.equals(r.labels().get("clientRequestId")))
                .findFirst();
        if (active.isPresent()) return active;
        return storedManifests.values().stream()
                .filter(m -> clientRequestId.equals(m.labels().get("clientRequestId")))
                .findFirst()
                .map(this::toRun);
    }

    public List<ExperimentRun> loadAll() {
        return loadAll(0, Integer.MAX_VALUE);
    }

    public long countAll() {
        return manifests.countAll();
    }

    /** Returns a page of runs sorted by startedAt (newest first), with artifacts eagerly loaded. */
    public List<ExperimentRun> loadAll(int page, int size) {
        Map<String, ExperimentRun> merged = new LinkedHashMap<>();
        for (RunManifest manifest : manifests.readAll(page, size)) {
            merged.put(manifest.runId(), toRun(manifest));
        }
        for (ExperimentRun run : activeRuns.values()) {
            merged.put(run.id(), run);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(ExperimentRun::startedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<RunArtifact> collectArtifacts(String runId, Path workDir,
                                               Map<String, String> parameters,
                                               RunnerAdapter adapter) {
        List<RunArtifact> artifacts = new ArrayList<>();
        if (adapter != null && Files.isDirectory(workDir)) {
            Path summary = adapter.locateSummary(workDir, parameters);
            if (summary != null && Files.isRegularFile(summary)) {
                Path canonical = workDir.resolve(K6RunnerAdapter.SUMMARY_FILE).toAbsolutePath();
                if (!summary.toAbsolutePath().equals(canonical)) {
                    try {
                        Files.copy(summary, canonical, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                        canonical = summary;
                    }
                }
                artifacts.add(new RunArtifact("k6-summary", "application/json",
                        canonical.toString(), "k6 summary JSON"));
            }
        }
        addIfPresent(artifacts, workDir.resolve("stdout.log"), "runner-stdout",
                "text/plain; charset=utf-8", "Bounded runner stdout");
        addIfPresent(artifacts, workDir.resolve("stderr.log"), "runner-stderr",
                "text/plain; charset=utf-8", "Bounded runner stderr");
        addIfPresent(artifacts, workDir.resolve("raw-k6.json"), "k6-raw",
                "application/json", "Raw k6 JSON output");
        addIfPresent(artifacts, workDir.resolve("plan.txt"), "explain-plan",
                "text/plain; charset=utf-8", "Captured EXPLAIN plan");
        return artifacts;
    }

    private static void addIfPresent(List<RunArtifact> artifacts, Path path,
                                     String kind, String contentType, String description) {
        if (Files.isRegularFile(path)) {
            artifacts.add(new RunArtifact(kind, contentType, path.toAbsolutePath().toString(), description));
        }
    }

    private Metrics readMetrics(Path workDir, Map<String, String> parameters, RunnerAdapter adapter) {
        if (adapter == null) return Metrics.empty();
        Path summary = adapter.locateSummary(workDir, parameters);
        if (summary == null || !Files.isRegularFile(summary)) return Metrics.empty();
        try {
            return adapter.parseSummary(summary);
        } catch (IOException e) {
            return Metrics.empty();
        }
    }

    private void writeManifest(ExperimentRun run, String outcome,
                               EnvironmentFingerprintService.Snapshot fingerprint,
                               Instant endedAt) {
        RunManifest stored = manifestWriter.write(run, outcome, fingerprint, endedAt);
        storedManifests.put(stored.runId(), stored);
    }

    private Optional<RunManifest> findStoredManifest(String runId) {
        RunManifest cached = storedManifests.get(runId);
        if (cached != null) return Optional.of(cached);
        return manifests.read(runId).map(manifest -> {
            storedManifests.put(manifest.runId(), manifest);
            return manifest;
        });
    }

    private ExperimentRun toRun(RunManifest manifest) {
        return new ExperimentRun(
                manifest.runId(),
                manifest.templateId(),
                manifest.state(),
                manifest.startedAt(),
                manifest.durationMs(),
                manifest.labels(),
                manifest.parameters(),
                Metrics.fromSummaryMap(manifest.summaryMetrics()),
                artifactsOf(manifest),
                manifest.appCommit());
    }

    private static List<RunArtifact> artifactsOf(RunManifest manifest) {
        return manifest.artifacts().stream()
                .map(a -> new RunArtifact(a.artifactId(), a.kind(), a.contentType(),
                        a.location(), a.description()))
                .toList();
    }

    private Map<String, String> validateParameters(ExperimentTemplate template, Map<String, String> parameters) {
        Map<String, String> input = parameters == null ? Map.of() : parameters;
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Set<String> known = new LinkedHashSet<>();

        for (ExperimentTemplate.ParameterField field : template.parameters()) {
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

    private String generateRunId(String templateId, Map<String, String> parameters) {
        String out = parameters.getOrDefault("OUT", UUID.randomUUID().toString().substring(0, 8));
        if (!SAFE_TAG.matcher(out).matches()) {
            throw new ParameterValidationException(
                    Map.of("OUT", List.of("must match [A-Za-z0-9._-], 1-64 characters")));
        }
        String base = templateId.toLowerCase() + "-" + out + "-" + System.currentTimeMillis();
        String candidate = base;
        int suffix = 1;
        while (activeRuns.containsKey(candidate) || manifests.read(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private void cleanupActiveRuns() {
        activeRuns.entrySet().removeIf(e -> TERMINAL_STATES.contains(e.getValue().state()));
    }

    private RunnerAdapter adapterOrNull() {
        return adapters.stream().filter(a -> a.name().equals("k6")).findFirst().orElse(null);
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
            return Math.min(TimeUnit.SECONDS.toMillis(seconds * 3 + 60), TimeUnit.MINUTES.toMillis(30));
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
        return Math.min(TimeUnit.MINUTES.toMillis(Math.max(minutes * 3, 5)), TimeUnit.MINUTES.toMillis(30));
    }

}
