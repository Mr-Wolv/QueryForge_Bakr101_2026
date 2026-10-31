package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import com.bakr.queryforge.artifact.RunManifest;
import com.bakr.queryforge.experiment.ExperimentRunState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentOrchestratorTest {

    @TempDir
    Path tempDir;

    private final ExperimentTemplateService templates = new ExperimentTemplateService();
    private ExperimentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ExperimentOrchestrator(
                templates,
                new ManifestStore(tempDir),
                new EnvironmentFingerprintService(),
                new RunEventBus(),
                List.of());
    }

    @Test
    void createRunReturnsQueuedRunWithValidParameters() {
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of(
                "RATE", "10",
                "DUR", "60",
                "OUT", "test-run"
        ), "client-1");

        assertThat(run.templateId()).isEqualTo("QF-001");
        assertThat(run.state()).isEqualTo("QUEUED");
        assertThat(run.startedAt()).isNotNull();
        assertThat(run.labels()).containsEntry("clientRequestId", "client-1");
        assertThat(orchestrator.findById(run.id())).isPresent();
    }

    @Test
    void createRunRejectsMissingParameter() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of("RATE", "10"), "client-2"))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(e -> assertThat(((ParameterValidationException) e).errors())
                        .containsKey("DUR"));
    }

    @Test
    void createRunRejectsIntegerBelowMin() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of(
                "RATE", "0",
                "DUR", "60",
                "OUT", "x"
        ), "client-3"))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(e -> assertThat(((ParameterValidationException) e).errors().get("RATE"))
                        .anyMatch(message -> message.contains("must be >= 1")));
    }

    @Test
    void createRunRejectsIntegerAboveMax() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of(
                "RATE", "999",
                "DUR", "60",
                "OUT", "x"
        ), "client-4"))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(e -> assertThat(((ParameterValidationException) e).errors().get("RATE"))
                        .anyMatch(message -> message.contains("must be <= 200")));
    }

    @Test
    void createRunRejectsInvalidEnum() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-002", Map.of(
                "MODE", "invalid",
                "RATE", "10",
                "DUR", "60"
        ), "client-5"))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(e -> assertThat(((ParameterValidationException) e).errors())
                        .containsKey("MODE"));
    }

    @Test
    void createRunRejectsDuplicateClientRequestId() {
        orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "a"), "dup-id");
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "b"), "dup-id"))
                .isInstanceOf(DuplicateRequestException.class)
                .hasMessageContaining("dup-id");
    }

    @Test
    void createRunRejectsUnknownTemplate() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-999", Map.of("X", "1"), "client-6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown template");
    }

    @Test
    void historyReadsStoredManifestsOnceAndUsesTheInMemoryIndex() {
        AtomicInteger readAllCalls = new AtomicInteger();
        ManifestStore indexedStore = new ManifestStore(tempDir) {
            @Override
            public List<RunManifest> readAll() {
                readAllCalls.incrementAndGet();
                return super.readAll();
            }
        };
        ExperimentOrchestrator indexedOrchestrator = new ExperimentOrchestrator(
                templates,
                indexedStore,
                new EnvironmentFingerprintService(),
                new RunEventBus(),
                List.of());

        indexedOrchestrator.loadAll();
        indexedOrchestrator.loadAll();

        assertThat(readAllCalls).hasValue(1);
    }

    @Test
    void cancelQueuedRunMarksCancelled() {
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of(
                "RATE", "10", "DUR", "60", "OUT", "cancel-run"
        ), "client-cancel");

        boolean cancelled = orchestrator.cancel(run.id());
        assertThat(cancelled).isTrue();
        assertThat(orchestrator.findById(run.id()))
                .map(ExperimentRun::state)
                .contains(ExperimentRunState.CANCELLED.name());
    }

    @Test
    void cancelAlreadyTerminalRunReturnsFalse() {
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of(
                "RATE", "10", "DUR", "60", "OUT", "term-run"
        ), "client-term");
        orchestrator.cancel(run.id());

        assertThat(orchestrator.cancel(run.id())).isFalse();
    }

    @Test
    void cancelUnknownRunReturnsFalse() {
        assertThat(orchestrator.cancel("nonexistent")).isFalse();
    }

    @Test
    void loadAllReturnsActiveRuns() {
        orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "p1"), "client-p1");
        orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "p2"), "client-p2");

        List<ExperimentRun> all = orchestrator.loadAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void countAllReturnsStoredManifestCount() {
        long before = orchestrator.countAll();
        orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "count1"), "client-count1");
        // createRun does not write a manifest; countAll only counts stored manifests
        assertThat(orchestrator.countAll()).isEqualTo(before);
    }

    @Test
    void manifestReturnsStoredFingerprint() {
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of(
                "RATE", "10", "DUR", "60", "OUT", "manifest-run"
        ), "client-manifest");
        orchestrator.run(run.id(), templates.findById("QF-001").orElseThrow(),
                Map.of("RATE", "10", "DUR", "60", "OUT", "manifest-run"));

        assertThat(orchestrator.manifest(run.id())).isPresent();
    }

    @Test
    void findByClientRequestIdFindsActiveRun() {
        ExperimentRun run = orchestrator.createRun("QF-001", Map.of(
                "RATE", "10", "DUR", "60", "OUT", "cid-run"
        ), "client-cid");

        assertThat(orchestrator.findByClientRequestId("client-cid"))
                .map(ExperimentRun::id)
                .contains(run.id());
    }
}
