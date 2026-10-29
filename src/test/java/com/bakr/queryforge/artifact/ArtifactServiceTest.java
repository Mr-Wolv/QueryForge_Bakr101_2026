package com.bakr.queryforge.artifact;

import com.bakr.queryforge.measurement.Metrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactServiceTest {

    private final ArtifactService service = new ArtifactService();

    @Test
    void resolveReturnsEmptyForUnknownId() {
        assertThat(service.resolve("does-not-exist")).isEmpty();
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
    void readPlanCachesResult() throws Exception {
        String first = service.readPlan("qf001-baseline");
        String second = service.readPlan("qf001-baseline");
        assertThat(first).isSameAs(second).isNotEmpty();
    }

    @Test
    void parsePercentilesExtractsIterationAndHttp() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
        Metrics metrics = Metrics.fromK6Node(node);
        assertThat(metrics.iterationPercentiles()).containsEntry("p50", new BigDecimal("10"))
                .containsEntry("p95", new BigDecimal("20"))
                .containsEntry("p99", new BigDecimal("30"));
        assertThat(metrics.httpPercentiles()).containsEntry("p50", new BigDecimal("8"))
                .containsEntry("p95", new BigDecimal("18"));
        assertThat(metrics.iterationCount()).isEqualTo(1234);
        assertThat(metrics.rps()).isCloseTo(56.78, org.assertj.core.data.Offset.offset(0.01));
        assertThat(metrics.testRunDurationMs()).isEqualTo(60000);
    }

    @Test
    void parsePercentilesHandlesMissingNodes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Metrics metrics = Metrics.fromK6Node(mapper.readTree("{}"));
        assertThat(metrics.iterationPercentiles()).isEmpty();
        assertThat(metrics.httpPercentiles()).isEmpty();
        assertThat(metrics.iterationCount()).isZero();
        assertThat(metrics.rps()).isZero();
        assertThat(metrics.testRunDurationMs()).isZero();
    }
}
