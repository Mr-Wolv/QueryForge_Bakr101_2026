package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Historical runs were persisted without their artifact list, so the run detail page reported
 * "No artifacts available" for runs whose manifest lists stderr and stdout.
 */
class ExperimentRunServiceArtifactsTest {

    private final ExperimentTemplateService templates = mock(ExperimentTemplateService.class);
    private final ExperimentOrchestrator orchestrator = mock(ExperimentOrchestrator.class);
    private final RunPersistenceService persistence = mock(RunPersistenceService.class);
    private final ExperimentRunService service = new ExperimentRunService(templates, orchestrator, persistence);

    @Test
    void fallsBackToManifestArtifactsWhenTheRunRecordHasNone() {
        ExperimentRun run = mock(ExperimentRun.class);
        when(persistence.findById("run-1")).thenReturn(Optional.of(run));
        when(run.artifacts()).thenReturn(List.of());
        when(orchestrator.manifest("run-1")).thenReturn(Optional.of(manifest()));

        List<RunArtifact> artifacts = service.artifacts("run-1");

        assertThat(artifacts).extracting(RunArtifact::artifactId).containsExactly("stderr");
        assertThat(artifacts.get(0).kind()).isEqualTo("runner-stderr");
        assertThat(artifacts.get(0).contentType()).isEqualTo("text/plain; charset=utf-8");
    }

    @Test
    void prefersRecordedArtifactsWhenTheRunHasThem() {
        ExperimentRun run = mock(ExperimentRun.class);
        when(persistence.findById("run-2")).thenReturn(Optional.of(run));
        when(run.artifacts()).thenReturn(List.of(
                new RunArtifact("summary", "k6-summary", "application/json", "/tmp/summary.json", "k6 summary")));

        List<RunArtifact> artifacts = service.artifacts("run-2");

        assertThat(artifacts).extracting(RunArtifact::artifactId).containsExactly("summary");
    }

    @Test
    void returnsEmptyWhenNeitherRecordNorManifestDescribeArtifacts() {
        when(persistence.findById("run-3")).thenReturn(Optional.empty());
        when(orchestrator.manifest("run-3")).thenReturn(Optional.empty());

        assertThat(service.artifacts("run-3")).isEmpty();
    }

    private static RunManifest manifest() {
        Instant now = Instant.parse("2026-11-01T13:40:22Z");
        return new RunManifest("run-1", "QF-000", "1.0", "FAILED", now, now, 539,
                "failed with exit code 107", Map.of(), Map.of(), Map.of(), "v1", "none", Map.of(),
                "abc123", Map.of(),
                List.of(new RunManifest.ArtifactEntry("stderr", "runner-stderr",
                        "text/plain; charset=utf-8", "/tmp/stderr.log", "Bounded runner stderr", "sum")),
                "manifest-sum");
    }
}
