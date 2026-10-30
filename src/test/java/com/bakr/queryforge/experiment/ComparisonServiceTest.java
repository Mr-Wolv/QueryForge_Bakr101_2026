package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparisonServiceTest {

    private final ExperimentOrchestrator orchestrator = createOrchestratorWithRuns();
    private final ExperimentRunService runs = new ExperimentRunService(
            new ExperimentTemplateService(), orchestrator
    );
    private final ComparisonService comparisonService = new ComparisonService(
            runs, new ExperimentTemplateService(), new ArtifactService()
    );
    private final ExportService exportService = new ExportService(
            runs, new ExperimentTemplateService(), new ArtifactService()
    );

    @Test
    void compareIncompatibleDifferentTemplate() throws IOException {
        String leftId = registerSucceededRun("QF-001");
        String rightId = registerSucceededRun("QF-002");

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("Different experiment templates"));
        assertThat(report.content()).contains("INCOMPATIBLE");
    }

    @Test
    void compareSameRunIdIsCompatible() throws IOException {
        String runId = registerSucceededRun("QF-001");
        ComparisonReport report = comparisonService.compare(runId, runId, "tester");
        assertThat(report.verdict().compatible()).isTrue();
    }

    @Test
    void compareRejectsNonexistentRun() {
        assertThatThrownBy(() -> comparisonService.compare("nonexistent-left", "nonexistent-right", "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void compareProducesArtifacts() throws IOException {
        String leftId = registerSucceededRun("QF-001");
        String rightId = registerSucceededRun("QF-001");

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.content()).contains("# Comparison Report");
        assertThat(report.artifacts()).hasSize(2);
        assertThat(report.artifacts()).anyMatch(a -> a.kind().equals("comparison-markdown"));
        assertThat(report.artifacts()).anyMatch(a -> a.kind().equals("comparison-html"));

        report.artifacts().forEach(a -> {
            try {
                Files.deleteIfExists(Path.of(a.location()));
            } catch (Exception ignored) {}
        });
    }

    @Test
    void exportMarkdownContainsRunDetails() throws IOException {
        String runId = registerSucceededRun("QF-001");
        String md = exportService.exportMarkdown(runId);
        assertThat(md).contains(runId);
        assertThat(md).contains("QF-001");
    }

    @Test
    void exportHtmlContainsRunDetails() throws IOException {
        String runId = registerSucceededRun("QF-001");
        String html = exportService.exportHtml(runId);
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains(runId);
    }

    @Test
    void persistAndLoadComparison() throws Exception {
        String leftId = registerSucceededRun("QF-001");
        String rightId = registerSucceededRun("QF-002");

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        String id = report.id();

        // Verify report was persisted by checking if loadAll finds it
        List<ComparisonReport> all = comparisonService.loadAll();
        assertThat(all).anyMatch(r -> r.id().equals(id));
    }

    private ExperimentOrchestrator createOrchestratorWithRuns() {
        return new ExperimentOrchestrator(
                new ExperimentTemplateService(),
                new ArtifactService(),
                List.of()
        );
    }

    private String registerSucceededRun(String templateId) {
        String runId = templateId.toLowerCase() + "-seed-" + System.nanoTime();
        ExperimentRun run = new ExperimentRun(
                runId, templateId, "SUCCEEDED",
                Instant.now().minusSeconds(30), 5000,
                Map.of("clientRequestId", "test-" + runId),
                Map.of(),
                com.bakr.queryforge.measurement.Metrics.empty(),
                List.of(),
                "unknown"
        );
        try {
            Field activeRunsField = ExperimentOrchestrator.class.getDeclaredField("activeRuns");
            activeRunsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ExperimentRun> activeRuns = (Map<String, ExperimentRun>) activeRunsField.get(orchestrator);
            activeRuns.put(runId, run);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return runId;
    }
}
