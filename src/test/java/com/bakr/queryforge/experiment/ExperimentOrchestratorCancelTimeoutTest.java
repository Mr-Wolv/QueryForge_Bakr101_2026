package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentOrchestratorCancelTimeoutTest {

    @TempDir
    Path tempDir;

    @Test
    void cancelReturnsFalseForUnknownRun() {
        ExperimentOrchestrator orchestrator = createOrchestrator();
        assertThat(orchestrator.cancel("nonexistent-run")).isFalse();
    }

    @Test
    void cancelTransitionsQueuedRunToCancelled() {
        ExperimentOrchestrator orchestrator = createOrchestrator();
        ExperimentRun run = orchestrator.createRun("QF-001",
                Map.of("RATE", "10", "DUR", "60", "OUT", "x"), "cid");
        assertThat(run.state()).isEqualTo("QUEUED");

        boolean cancelled = orchestrator.cancel(run.id());
        assertThat(cancelled).isTrue();

        Optional<ExperimentRun> updated = orchestrator.findById(run.id());
        assertThat(updated).isPresent();
        assertThat(updated.get().state()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelWritesCanonicalManifest() throws Exception {
        ExperimentOrchestrator orchestrator = createOrchestrator();
        ExperimentRun run = orchestrator.createRun("QF-001",
                Map.of("RATE", "10", "DUR", "60", "OUT", "x"), "cid");
        orchestrator.cancel(run.id());

        Path manifest = tempDir.resolve(run.id()).resolve("manifest.json");
        assertThat(Files.isRegularFile(manifest)).isTrue();
        String content = Files.readString(manifest);
        assertThat(content).contains("CANCELLED");
        assertThat(content).contains("migrationVersion");
        assertThat(content).contains("indexState");
        assertThat(content).contains("resourceLimits");
        assertThat(content).contains("appCommit");
        assertThat(content).contains("checksumSha256");
        // ISO-8601 timestamps, not numeric epochs.
        assertThat(content).contains("\"startedAt\" : \"");
    }

    @Test
    void restartRecoveryMarksRunningAsFailed() throws Exception {
        String runId = "test-recovery-" + System.nanoTime();
        Files.createDirectories(tempDir);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("templateId", "QF-001");
        manifest.put("state", "RUNNING");
        manifest.put("startedAt", Instant.now().minusSeconds(60).toString());
        manifest.put("durationMs", 0);
        manifest.put("outcome", "interrupted");
        manifest.put("artifacts", List.of());
        Files.writeString(tempDir.resolve(runId + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest));

        ExperimentOrchestrator orchestrator = createOrchestrator();
        Optional<ExperimentRun> found = orchestrator.findById(runId);
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo("FAILED");

        Files.deleteIfExists(tempDir.resolve(runId).resolve("manifest.json"));
    }

    @Test
    void restartRecoveryKeepsTerminalStatesVisible() throws Exception {
        String runId = "test-recovery-skip-" + System.nanoTime();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("templateId", "QF-001");
        manifest.put("state", "SUCCEEDED");
        manifest.put("startedAt", Instant.now().minusSeconds(60).toString());
        manifest.put("durationMs", 1000);
        manifest.put("outcome", "completed");
        manifest.put("artifacts", List.of());
        Files.writeString(tempDir.resolve(runId + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest));

        ExperimentOrchestrator orchestrator = createOrchestrator();
        Optional<ExperimentRun> found = orchestrator.findById(runId);
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo("SUCCEEDED");

        Files.deleteIfExists(tempDir.resolve(runId + ".json"));
    }

    @Test
    void legacyNumericTimestampsAreRead() throws Exception {
        String runId = "test-numeric-" + System.nanoTime();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("templateId", "QF-001");
        manifest.put("state", "SUCCEEDED");
        manifest.put("startedAt", Instant.parse("2026-01-01T00:00:00Z").getEpochSecond());
        manifest.put("endedAt", Instant.parse("2026-01-01T00:01:00Z").getEpochSecond());
        manifest.put("durationMs", 60000);
        manifest.put("artifacts", List.of());
        Files.writeString(tempDir.resolve(runId + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(manifest));

        ExperimentOrchestrator orchestrator = createOrchestrator();
        ExperimentRun run = orchestrator.findById(runId).orElseThrow();
        assertThat(run.startedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));

        Files.deleteIfExists(tempDir.resolve(runId + ".json"));
    }

    private ExperimentOrchestrator createOrchestrator() {
        return new ExperimentOrchestrator(
                new ExperimentTemplateService(),
                new ManifestStore(tempDir),
                new EnvironmentFingerprintService(),
                new RunEventBus(),
                List.of());
    }
}
