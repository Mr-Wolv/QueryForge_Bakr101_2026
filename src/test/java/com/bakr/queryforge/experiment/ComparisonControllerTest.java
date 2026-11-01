package com.bakr.queryforge.experiment;

import com.bakr.queryforge.api.dto.CompareRequest;
import com.bakr.queryforge.artifact.ManifestStore;
import com.bakr.queryforge.artifact.RunManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparisonControllerTest {

    @TempDir
    Path tempDir;
    ManifestStore manifests;

    private ComparisonController controller() {
        manifests = new ManifestStore(tempDir);
        EnvironmentFingerprintService fingerprints = new EnvironmentFingerprintService();
        ExperimentOrchestrator orchestrator = new ExperimentOrchestrator(
                new ExperimentTemplateService(), manifests, fingerprints,
                new RunEventBus(), List.of());
        ExperimentRunService runs = new ExperimentRunService(new ExperimentTemplateService(), orchestrator, null);
        ComparisonService comparisonService = new ComparisonService(runs, tempDir.resolve("comparisons"));
        return new ComparisonController(comparisonService, runs);
    }

    private String seedRun(String templateId, Map<String, String> parameters,
                           Map<String, Object> fingerprint, com.bakr.queryforge.measurement.Metrics metrics) throws IOException {
        String runId = templateId.toLowerCase() + "-seed-" + System.nanoTime();
        Map<String, Object> summary = new LinkedHashMap<>();
        if (metrics != null) {
            metrics.iterationPercentiles().forEach((k, v) -> summary.put("iteration." + k, v));
            metrics.httpPercentiles().forEach((k, v) -> summary.put("http." + k, v));
            summary.put("iterations", metrics.iterationCount());
            summary.put("rps", metrics.rps());
            summary.put("testRunDurationMs", metrics.testRunDurationMs());
        }
        RunManifest manifest = new RunManifest(
                runId, templateId, "1.0", "SUCCEEDED",
                Instant.now().minusSeconds(30), Instant.now(), 30_000,
                "completed", parameters, Map.of(), fingerprint,
                "V1", String.valueOf(fingerprint.get("indexState")),
                Map.of("cpuCores", 4, "memoryMb", 4096),
                String.valueOf(fingerprint.get("appCommit")), summary, List.of(), null);
        manifests.write(manifest);
        return runId;
    }

    private static Map<String, Object> fingerprint(int major, long rows, String indexState, String commit) {
        return Map.of(
                "available", true,
                "appCommit", commit,
                "databaseProduct", "PostgreSQL",
                "postgresVersion", major + ".4",
                "postgresMajorVersion", major,
                "rowCount", rows,
                "indexState", indexState,
                "cpuCores", 4,
                "memoryMb", 4096);
    }

    private static com.bakr.queryforge.measurement.Metrics metrics(int p95) {
        return new com.bakr.queryforge.measurement.Metrics(60_000,
                java.util.Map.of("p95", new java.math.BigDecimal(p95), "p50", new java.math.BigDecimal(p95 / 2)),
                java.util.Map.of("p95", new java.math.BigDecimal(p95)),
                100, 10.0);
    }

    @Test
    void compareCreatesReportAndReturnsCreated() throws IOException {
        ComparisonController c = controller();
        String left = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String right = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));

        ResponseEntity<com.bakr.queryforge.experiment.ComparisonReport> response =
                c.compare(new CompareRequest(left, right, "tester"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().verdict().compatible()).isTrue();
    }

    @Test
    void compareRejectsSameRunIds() {
        ComparisonController c = controller();
        assertThatThrownBy(() -> c.compare(new CompareRequest("run-1", "run-1", "tester")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("run ids must differ");
    }

    @Test
    void compareRejectsUnknownRun() {
        ComparisonController c = controller();
        assertThatThrownBy(() -> c.compare(new CompareRequest("unknown-left", "unknown-right", "tester")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getReturnsNotFoundForUnknownComparison() {
        ComparisonController c = controller();
        ResponseEntity<com.bakr.queryforge.experiment.ComparisonReport> response =
                c.get("nonexistent");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listReturnsEmptyInitially() {
        ComparisonController c = controller();
        assertThat(c.list()).isEmpty();
    }

    @Test
    void exportRejectsUnsupportedFormat() throws IOException {
        ComparisonController c = controller();
        String left = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String right = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));
        com.bakr.queryforge.experiment.ComparisonReport report =
                c.compare(new CompareRequest(left, right, "tester")).getBody();

        assertThatThrownBy(() -> c.export(report.id(), "xml"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void exportMarkdownReturnsContent() throws IOException {
        ComparisonController c = controller();
        String left = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String right = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));
        com.bakr.queryforge.experiment.ComparisonReport report =
                c.compare(new CompareRequest(left, right, "tester")).getBody();

        ResponseEntity<String> response = c.export(report.id(), "markdown");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# Comparison Report");
    }

    @Test
    void exportHtmlReturnsContent() throws IOException {
        ComparisonController c = controller();
        String left = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String right = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));
        com.bakr.queryforge.experiment.ComparisonReport report =
                c.compare(new CompareRequest(left, right, "tester")).getBody();

        ResponseEntity<String> response = c.export(report.id(), "html");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("<!doctype html>");
    }
}
