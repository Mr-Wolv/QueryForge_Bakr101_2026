package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentOrchestratorTest {

    private final ExperimentTemplateService templates = new ExperimentTemplateService();
    private final ExperimentOrchestrator orchestrator = new ExperimentOrchestrator(
            templates, new com.bakr.queryforge.artifact.ArtifactService(), List.of());

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
        assertThat(orchestrator.findById(run.id())).isPresent();
    }

    @Test
    void createRunRejectsMissingParameter() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of("RATE", "10"), "client-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing parameter");
    }

    @Test
    void createRunRejectsIntegerBelowMin() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of(
                "RATE", "0",
                "DUR", "60",
                "OUT", "x"
        ), "client-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below min");
    }

    @Test
    void createRunRejectsIntegerAboveMax() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of(
                "RATE", "999",
                "DUR", "60",
                "OUT", "x"
        ), "client-4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("above max");
    }

    @Test
    void createRunRejectsInvalidEnum() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-002", Map.of(
                "MODE", "invalid",
                "RATE", "10",
                "DUR", "60"
        ), "client-5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be one of");
    }

    @Test
    void createRunRejectsDuplicateClientRequestId() {
        orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "a"), "dup-id");
        assertThatThrownBy(() -> orchestrator.createRun("QF-001", Map.of("RATE", "10", "DUR", "60", "OUT", "b"), "dup-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate request id");
    }

    @Test
    void createRunRejectsUnknownTemplate() {
        assertThatThrownBy(() -> orchestrator.createRun("QF-999", Map.of("X", "1"), "client-6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown template");
    }
}
