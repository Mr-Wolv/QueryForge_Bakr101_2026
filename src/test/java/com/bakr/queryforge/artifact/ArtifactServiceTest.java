package com.bakr.queryforge.artifact;

import com.bakr.queryforge.measurement.Metrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactServiceTest {

    private final ArtifactService service = new ArtifactService();

    @TempDir
    Path tempDir;

    @Test
    void resolveReturnsEmptyForUnknownId() {
        assertThat(service.resolve("does-not-exist")).isEmpty();
    }

    @Test
    void resolveForRunRejectsNullAndBlank() {
        assertThat(service.resolveForRun("run-1", null)).isEmpty();
        assertThat(service.resolveForRun("run-1", "")).isEmpty();
        assertThat(service.resolveForRun("run-1", "   ")).isEmpty();
    }

    @Test
    void resolveForRunRejectsPathTraversal() {
        assertThat(service.resolveForRun("run-1", "../etc/passwd")).isEmpty();
        assertThat(service.resolveForRun("run-1", "foo/bar")).isEmpty();
        assertThat(service.resolveForRun("run-1", "foo\\bar")).isEmpty();
    }

    @Test
    void resolveRejectsPathTraversal() {
        assertThat(service.resolve("../etc/passwd")).isEmpty();
        assertThat(service.resolve("foo/bar")).isEmpty();
        assertThat(service.resolve("foo\\bar")).isEmpty();
    }

    @Test
    void guessContentTypeByExtension() {
        assertThat(service.guessContentType(java.nio.file.Path.of("a.json"))).isEqualTo("application/json");
        assertThat(service.guessContentType(java.nio.file.Path.of("a.txt"))).isEqualTo("text/plain; charset=utf-8");
        assertThat(service.guessContentType(java.nio.file.Path.of("a.csv"))).isEqualTo("text/csv");
        assertThat(service.guessContentType(java.nio.file.Path.of("a.png"))).isEqualTo("image/png");
        assertThat(service.guessContentType(java.nio.file.Path.of("a.unknown"))).isEqualTo("application/octet-stream");
    }

    @Test
    void metadataContainsSizeWhenPathExists() {
        assertThat(service.metadata("qf001-baseline")).isPresent();
        Map<String, String> meta = service.metadata("qf001-baseline").orElseThrow();
        assertThat(meta).containsKeys("path", "contentType", "sizeBytes");
        assertThat(Long.parseLong(meta.get("sizeBytes"))).isGreaterThan(0);
    }

    @Test
    void metadataReturnsEmptyWhenFileDoesNotExist() {
        assertThat(service.metadata("does-not-exist")).isEmpty();
    }

    @Test
    void readPlanCachesResult() throws Exception {
        String first = service.readPlan("qf001-baseline");
        String second = service.readPlan("qf001-baseline");
        assertThat(first).isSameAs(second).isNotEmpty();
    }

    @Test
    void readTextReturnsEmptyForNonExistentPath() {
        assertThat(service.readText(tempDir.resolve("missing.txt"))).isEmpty();
    }

    @Test
    void parsePercentilesExtractsIterationAndHttp() throws Exception {
        var mapper = new ObjectMapper();
        var node = mapper.readTree("""
                {
                  "metrics": {
                    "iteration_duration": {
                      "values": { "p(50)": 10, "p(95)": 20, "p(99)": 30, "avg": 15, "min": 5, "max": 40 }
                    },
                    "http_req_duration": {
                      "values": { "p(50)": 8, "p(95)": 18, "p(99)": 28 }
                    },
                    "iterations": {
                      "values": { "count": 1234, "rate": 56.78 }
                    }
                  },
                  "state": { "testRunDurationMs": 60000 }
                }
                """);
        Map<String, BigDecimal> result = service.parsePercentiles(node);
        assertThat(result).containsEntry("p50", new BigDecimal("10"))
                .containsEntry("p95", new BigDecimal("20"))
                .containsEntry("p99", new BigDecimal("30"))
                .containsEntry("avg", new BigDecimal("15"))
                .containsEntry("min", new BigDecimal("5"))
                .containsEntry("max", new BigDecimal("40"))
                .containsEntry("http_p50", new BigDecimal("8"))
                .containsEntry("http_p95", new BigDecimal("18"))
                .containsEntry("http_p99", new BigDecimal("28"))
                .containsEntry("iterations", new BigDecimal("1234"))
                .containsEntry("rps", new BigDecimal("56.78"));
    }

    @Test
    void parsePercentilesHandlesVariousStructures() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(service.parsePercentiles(null)).isEmpty();
        assertThat(service.parsePercentiles(mapper.readTree("42"))).isEmpty();
        assertThat(service.parsePercentiles(mapper.readTree("{}"))).isEmpty();

        JsonNode node = mapper.readTree("{\"metrics\": {\"http_req_duration\": {\"values\": {\"p(50)\": 8}}}}");
        Map<String, BigDecimal> result = service.parsePercentiles(node);
        assertThat(result).containsEntry("http_p50", new BigDecimal("8"));

        result = service.parsePercentiles(mapper.readTree("{\"metrics\": {\"iteration_duration\": {\"values\": {\"p(50)\": null}}}}"));
        assertThat(result).isEmpty();

        result = service.parsePercentiles(mapper.readTree("{\"metrics\": {\"iteration_duration\": {\"values\": {\"p(50)\": \"not-a-number\"}}}}"));
        assertThat(result).isEmpty();
    }

    @Test
    void parsePercentilesHandlesMissingNodes() throws Exception {
        var mapper = new ObjectMapper();
        Metrics metrics = Metrics.fromK6Node(mapper.readTree("{}"));
        assertThat(metrics.iterationPercentiles()).isEmpty();
        assertThat(metrics.httpPercentiles()).isEmpty();
        assertThat(metrics.iterationCount()).isZero();
        assertThat(metrics.rps()).isZero();
        assertThat(metrics.testRunDurationMs()).isZero();
    }

    @Test
    void catalogForRunIncludesManifestAndDeduplicates() {
        List<RunArtifact> artifacts = List.of(
                new RunArtifact("manifest", "application/json", "/tmp/x", "dup"),
                new RunArtifact("k6-summary", "application/json", "/tmp/y", "summary")
        );
        List<RunArtifact> catalog = service.catalogForRun("qf001-baseline", artifacts);
        assertThat(catalog).anyMatch(a -> a.kind().equals("manifest"));
        assertThat(catalog).anyMatch(a -> a.kind().equals("k6-summary"));
        long manifestCount = catalog.stream().filter(a -> a.kind().equals("manifest")).count();
        assertThat(manifestCount).isEqualTo(1);
    }

    @Test
    void resolveForRunFallsBackToResolveForMissingManifest() {
        assertThat(service.resolveForRun("nonexistent-run", "some-artifact")).isEmpty();
    }
}
