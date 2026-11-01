package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import com.bakr.queryforge.artifact.RunManifest;
import com.bakr.queryforge.measurement.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparisonServiceTest {

    @TempDir
    Path tempDir;

    private ManifestStore manifests;
    private ExperimentRunService runs;
    private ComparisonService comparisonService;
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        manifests = new ManifestStore(tempDir);
        ExperimentOrchestrator orchestrator = new ExperimentOrchestrator(
                new ExperimentTemplateService(),
                manifests,
                new EnvironmentFingerprintService(),
                new RunEventBus(),
                List.of());
        runs = new ExperimentRunService(new ExperimentTemplateService(), orchestrator, null);
        comparisonService = new ComparisonService(runs, tempDir.resolve("comparisons"));
        exportService = new ExportService(runs, new ExperimentTemplateService());
    }

    @Test
    void compareIncompatibleDifferentTemplate() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-002", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("Different experiment templates"));
        assertThat(report.content()).contains("INCOMPATIBLE");
    }

    @Test
    void compareSameRunIdIsCompatible() throws IOException {
        String runId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        ComparisonReport report = comparisonService.compare(runId, runId, "tester");
        assertThat(report.verdict().compatible()).isTrue();
    }

    @Test
    void differentPostgresMajorVersionIsABlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(16, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("PostgreSQL major version"));
    }

    @Test
    void differentRowCountIsABlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 100_000, "baseline", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("row counts"));
    }

    @Test
    void differentIndexStateIsABlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "indexed", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("index state"));
    }

    @Test
    void appCommitDifferenceIsAWarningNotABlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "def"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isTrue();
        assertThat(report.verdict().warnings()).anyMatch(w -> w.contains("commit differs"));
    }

    @Test
    void metricsDeltaContainsPercentChange() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(50));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        MetricDelta p95 = report.metricsDelta().get("iteration.p95");
        assertThat(p95).isNotNull();
        assertThat(p95.left()).isEqualByComparingTo("100");
        assertThat(p95.right()).isEqualByComparingTo("50");
        assertThat(p95.deltaPercent()).isEqualByComparingTo("-50");
        assertThat(report.content()).contains("Metric Deltas");
    }

    @Test
    void compareRejectsNonexistentRun() {
        assertThatThrownBy(() -> comparisonService.compare("nonexistent-left", "nonexistent-right", "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void compareProducesArtifacts() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.content())
                .contains("# Comparison Report", leftId, rightId, "## Verdict", "## Metric Deltas")
                .doesNotContain("[# th:", "[/]");
        assertThat(report.artifacts()).hasSize(2);
        assertThat(report.artifacts()).anyMatch(a -> a.kind().equals("comparison-markdown"));
        assertThat(report.artifacts()).anyMatch(a -> a.kind().equals("comparison-html"));
        assertThat(report.artifacts()).allMatch(a -> Files.isRegularFile(Path.of(a.location())));
        String htmlPath = report.artifacts().stream()
                .filter(a -> a.kind().equals("comparison-html"))
                .findFirst().orElseThrow().location();
        assertThat(Files.readString(Path.of(htmlPath)))
                .contains("<!doctype html>")
                .contains("Metric Deltas");
    }

    @Test
    void exportMarkdownContainsRunDetailsAndFingerprint() throws IOException {
        String runId = seedRun("QF-001", Map.of("RATE", "10"),
                fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String md = exportService.exportMarkdown(runId);
        assertThat(md).contains(runId);
        assertThat(md).contains("QF-001");
        assertThat(md).contains("postgresVersion");
        assertThat(md).contains("Manifest checksum");
    }

    @Test
    void exportHtmlContainsRunDetails() throws IOException {
        String runId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String html = exportService.exportHtml(runId);
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains(runId);
    }

    @Test
    void persistAndLoadComparisonKeepsMetricsDelta() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        List<ComparisonReport> all = comparisonService.loadAll();
        assertThat(all).anyMatch(r -> r.id().equals(report.id()));
        ComparisonReport loaded = comparisonService.findById(report.id()).orElseThrow();
        assertThat(loaded.metricsDelta()).containsKey("iteration.p95");
        assertThat(loaded.createdAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }

    private String seedRun(String templateId, Map<String, String> parameters,
                           Map<String, Object> fingerprint, Metrics metrics) throws IOException {
        String runId = templateId.toLowerCase() + "-seed-" + System.nanoTime();
        Map<String, Object> summary = new LinkedHashMap<>();
        metrics.iterationPercentiles().forEach((k, v) -> summary.put("iteration." + k, v));
        metrics.httpPercentiles().forEach((k, v) -> summary.put("http." + k, v));
        summary.put("iterations", metrics.iterationCount());
        summary.put("rps", metrics.rps());
        summary.put("testRunDurationMs", metrics.testRunDurationMs());

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
        Map<String, Object> fp = new LinkedHashMap<>();
        fp.put("available", true);
        fp.put("appCommit", commit);
        fp.put("databaseProduct", "PostgreSQL");
        fp.put("postgresVersion", major + ".4");
        fp.put("postgresMajorVersion", major);
        fp.put("rowCount", rows);
        fp.put("indexState", indexState);
        fp.put("cpuCores", 4);
        fp.put("memoryMb", 4096);
        return fp;
    }

    private static Map<String, Object> fingerprint(int major, long rows, String indexState, String commit, String pgVersion) {
        Map<String, Object> fp = fingerprint(major, rows, indexState, commit);
        fp.put("postgresVersion", pgVersion);
        return fp;
    }

    private static Map<String, Object> fingerprint(int major, long rows, String indexState, String commit, String pgVersion, String dbProduct) {
        Map<String, Object> fp = fingerprint(major, rows, indexState, commit, pgVersion);
        fp.put("databaseProduct", dbProduct);
        return fp;
    }

    private static Metrics metrics(int p95) {
        return new Metrics(60_000,
                Map.of("p95", BigDecimal.valueOf(p95), "p50", BigDecimal.valueOf(p95 / 2)),
                Map.of("p95", BigDecimal.valueOf(p95)),
                100, 10.0);
    }

    @Test
    void compareDifferentQueryParameterIsBlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of("DATASET", "A"), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of("DATASET", "B"), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("Different DATASET"));
    }

    @Test
    void compareDifferentWorkloadParameterIsWarning() throws IOException {
        String leftId = seedRun("QF-001", Map.of("RATE", "10"), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        String rightId = seedRun("QF-001", Map.of("RATE", "20"), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isTrue();
        assertThat(report.verdict().warnings()).anyMatch(w -> w.contains("Workload parameter RATE differs"));
    }

    @Test
    void compareNonSucceededStateIsBlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc"), metrics(100));
        Map<String, Object> fp = fingerprint(17, 1_000_000, "baseline", "abc");
        String rightId = seedRunFailed("QF-001", Map.of(), fp, metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("Both runs must be SUCCEEDED"));
    }

    @Test
    void compareDifferentPostgresPatchVersionIsWarning() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc", "17.4"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc", "17.5"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isTrue();
        assertThat(report.verdict().warnings()).anyMatch(w -> w.contains("PostgreSQL patch version differs"));
    }

    @Test
    void compareDifferentDatabaseEngineIsBlocker() throws IOException {
        String leftId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc", "17.4", "PostgreSQL"), metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "abc", "17.4", "MySQL"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isFalse();
        assertThat(report.verdict().blockers()).anyMatch(b -> b.contains("Different database engines"));
    }

    @Test
    void compareFingerprintUnavailableIsWarning() throws IOException {
        Map<String, Object> fp = Map.of("available", false, "appCommit", "abc", "databaseProduct", "PostgreSQL",
                "postgresVersion", "17.4", "postgresMajorVersion", 17, "rowCount", 1_000_000L,
                "indexState", "baseline", "cpuCores", 4, "memoryMb", 4096);
        String leftId = seedRun("QF-001", Map.of(), fp, metrics(100));
        String rightId = seedRun("QF-001", Map.of(), fingerprint(17, 1_000_000, "baseline", "def"), metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().compatible()).isTrue();
        assertThat(report.verdict().warnings()).anyMatch(w -> w.contains("fingerprint was unavailable"));
    }

    @Test
    void compareDifferentCpuCoresIsWarning() throws IOException {
        Map<String, Object> leftFp = fingerprint(17, 1_000_000, "baseline", "abc");
        leftFp.put("cpuCores", 8);
        Map<String, Object> rightFp = fingerprint(17, 1_000_000, "baseline", "abc");
        rightFp.put("cpuCores", 4);
        String leftId = seedRun("QF-001", Map.of(), leftFp, metrics(100));
        String rightId = seedRun("QF-001", Map.of(), rightFp, metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().warnings()).anyMatch(w -> w.contains("Host cpuCores differs"));
    }

    @Test
    void compareDifferentMemoryMbIsWarning() throws IOException {
        Map<String, Object> leftFp = fingerprint(17, 1_000_000, "baseline", "abc");
        leftFp.put("memoryMb", 8192);
        Map<String, Object> rightFp = fingerprint(17, 1_000_000, "baseline", "abc");
        rightFp.put("memoryMb", 4096);
        String leftId = seedRun("QF-001", Map.of(), leftFp, metrics(100));
        String rightId = seedRun("QF-001", Map.of(), rightFp, metrics(90));

        ComparisonReport report = comparisonService.compare(leftId, rightId, "tester");
        assertThat(report.verdict().warnings()).anyMatch(w -> w.contains("Host memoryMb differs"));
    }

    private String seedRunFailed(String templateId, Map<String, String> parameters,
                                 Map<String, Object> fingerprint, Metrics metrics) throws IOException {
        String runId = templateId.toLowerCase() + "-failed-" + System.nanoTime();
        RunManifest manifest = new RunManifest(
                runId, templateId, "1.0", "FAILED",
                Instant.now().minusSeconds(30), Instant.now(), 30_000,
                "completed", parameters, Map.of(), fingerprint,
                "V1", String.valueOf(fingerprint.get("indexState")),
                Map.of("cpuCores", 4, "memoryMb", 4096),
                String.valueOf(fingerprint.get("appCommit")), Map.of(), List.of(), null);
        manifests.write(manifest);
        return runId;
    }
}
