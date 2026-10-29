package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentOrchestratorCancelTimeoutTest {

    @Test
    void cancelReturnsFalseForUnknownRun() throws Exception {
        ExperimentOrchestrator orchestrator = createOrchestrator();
        assertThat(orchestrator.cancel("nonexistent-run")).isFalse();
    }

    @Test
    void cancelTransitionsQueuedRunToCancelled() throws Exception {
        ExperimentOrchestrator orchestrator = createOrchestrator();
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "x"), "cid");
        assertThat(run.state()).isEqualTo("QUEUED");

        boolean cancelled = orchestrator.cancel(run.id());
        assertThat(cancelled).isTrue();

        Optional<ExperimentRun> updated = orchestrator.findById(run.id());
        assertThat(updated).isPresent();
        assertThat(updated.get().state()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelWritesManifest() throws Exception {
        ExperimentOrchestrator orchestrator = createOrchestrator();
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "x"), "cid");
        orchestrator.cancel(run.id());

        Path manifest = Path.of("benchmark", "runs", run.id() + ".json");
        assertThat(Files.isRegularFile(manifest)).isTrue();
        String content = Files.readString(manifest);
        assertThat(content).contains("CANCELLED");
        assertThat(content).contains("migrationVersion");
        assertThat(content).contains("indexState");
        assertThat(content).contains("resourceLimits");
        assertThat(content).contains("appCommit");
        Files.deleteIfExists(manifest);
    }

    @Test
    void restartRecoveryMarksRunningAsFailed() throws Exception {
        String runId = "test-recovery-" + System.nanoTime();
        Path runsDir = Path.of("benchmark", "runs");
        Files.createDirectories(runsDir);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("templateId", "QF-001");
        manifest.put("state", "RUNNING");
        manifest.put("startedAt", Instant.now().minusSeconds(60).toString());
        manifest.put("durationMs", 0);
        manifest.put("outcome", "interrupted");
        manifest.put("artifacts", List.of());
        Files.writeString(runsDir.resolve(runId + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest));

        ExperimentOrchestrator orchestrator = createOrchestrator();
        Optional<ExperimentRun> found = orchestrator.findById(runId);
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo("FAILED");

        Files.deleteIfExists(runsDir.resolve(runId + ".json"));
    }

    @Test
    void restartRecoverySkipsTerminalStates() throws Exception {
        String runId = "test-recovery-skip-" + System.nanoTime();
        Path runsDir = Path.of("benchmark", "runs");
        Files.createDirectories(runsDir);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("templateId", "QF-001");
        manifest.put("state", "SUCCEEDED");
        manifest.put("startedAt", Instant.now().minusSeconds(60).toString());
        manifest.put("durationMs", 1000);
        manifest.put("outcome", "completed");
        manifest.put("artifacts", List.of());
        Files.writeString(runsDir.resolve(runId + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest));

        ExperimentOrchestrator orchestrator = createOrchestrator();
        Optional<ExperimentRun> found = orchestrator.findById(runId);
        assertThat(found).isEmpty();

        Files.deleteIfExists(runsDir.resolve(runId + ".json"));
    }

    private ExperimentOrchestrator createOrchestrator() {
        return new ExperimentOrchestrator(
                new ExperimentTemplateService(),
                new ArtifactService(),
                List.of()
        );
    }
}
