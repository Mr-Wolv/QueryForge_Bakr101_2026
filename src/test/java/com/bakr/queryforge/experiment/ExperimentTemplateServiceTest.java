package com.bakr.queryforge.experiment;

import com.bakr.queryforge.measurement.Metrics;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentTemplateServiceTest {

    private final ExperimentTemplateService templates = new ExperimentTemplateService();

    @Test
    void findAllReturnsFiveTemplates() {
        List<ExperimentTemplate> all = templates.findAll();
        assertThat(all).hasSize(5);
        assertThat(all.get(0).id()).isEqualTo("QF-000");
        assertThat(all.get(4).id()).isEqualTo("QF-004");
    }

    @Test
    void findByIdReturnsTemplateOrEmpty() {
        assertThat(templates.findById("QF-001")).isPresent();
        assertThat(templates.findById("QF-001")).get().hasFieldOrPropertyWithValue("title",
                "Filtered search: baseline vs composite index");
        assertThat(templates.findById("QF-999")).isEmpty();
    }

    @Test
    void parametersHaveBounds() {
        ExperimentTemplate qf001 = templates.findById("QF-001").orElseThrow();
        assertThat(qf001.parameters()).hasSize(3);
        assertThat(qf001.parameters().get(0).min()).isEqualTo(1);
        assertThat(qf001.parameters().get(0).max()).isEqualTo(200);
        assertThat(qf001.parameters().get(0).type()).isEqualTo("integer");
    }

    @Test
    void destructiveFlagIsCorrect() {
        assertThat(templates.findById("QF-004")).get().hasFieldOrPropertyWithValue("destructive", true);
        assertThat(templates.findById("QF-001")).get().hasFieldOrPropertyWithValue("destructive", false);
    }

    @Test
    void findRunsByTemplateFilters() {
        List<ExperimentRun> runs = templates.findRunsByTemplate("QF-001");
        assertThat(runs).allMatch(r -> r.templateId().equals("QF-001"));
    }

    @Test
    void loadRunsParsesKnownResultFiles() throws IOException {
        List<ExperimentRun> runs = templates.loadRuns();
        assertThat(runs).isNotEmpty();
        assertThat(runs).allMatch(r -> r.id() != null && r.state() != null);
    }

    @Test
    void parseRunAttachesKnownPlanArtifacts() throws IOException {
        List<ExperimentRun> runs = templates.loadRuns();
        ExperimentRun withPlan = runs.stream()
                .filter(r -> r.artifacts().stream().anyMatch(a -> a.kind().equals("explain-plan")))
                .findFirst()
                .orElse(null);
        assertThat(withPlan).isNotNull();
        assertThat(withPlan.metrics().iterationPercentiles()).isNotEmpty();
    }

    @Test
    void inferTemplateIdMapsKnownPrefixes() {
        assertThat(ExperimentTemplateService.inferTemplateId("qf001-baseline-search-1M-r10")).isEqualTo("QF-001");
        assertThat(ExperimentTemplateService.inferTemplateId("qf003-vus50")).isEqualTo("QF-003");
        assertThat(ExperimentTemplateService.inferTemplateId("unknown-run")).isEqualTo("UNKNOWN");
    }

    @Test
    void inferStartedAtUsesFileTimeWhenPresent() throws Exception {
        Path dir = Path.of("benchmark", "results");
        Files.createDirectories(dir);
        String fakeName = "test-infer-started-at-" + System.nanoTime();
        Path fake = dir.resolve(fakeName + ".json");
        Files.writeString(fake, "{}");
        long epochMilli = fake.toFile().lastModified();
        Instant inferred = ExperimentTemplateService.inferStartedAt(fakeName,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{}"));
        assertThat(inferred.toEpochMilli()).isCloseTo(epochMilli, org.assertj.core.data.Offset.offset(5000L));
        Files.deleteIfExists(fake);
    }
}
