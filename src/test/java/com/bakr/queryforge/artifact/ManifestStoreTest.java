package com.bakr.queryforge.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writeUsesCanonicalLayoutAndIsoTimestamps() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        RunManifest manifest = manifest("run-1", Instant.parse("2026-01-01T00:00:00Z"));

        RunManifest stamped = store.write(manifest);

        Path canonical = tempDir.resolve("run-1").resolve("manifest.json");
        assertThat(Files.isRegularFile(canonical)).isTrue();
        assertThat(Files.isRegularFile(tempDir.resolve("run-1.json"))).isFalse();
        String json = Files.readString(canonical);
        assertThat(json).contains("\"startedAt\" : \"2026-01-01T00:00:00Z\"");
        assertThat(json).contains("checksumSha256");
        assertThat(stamped.checksumSha256()).isNotBlank();
        assertThat(store.checksum(stamped)).isEqualTo(stamped.checksumSha256());
    }

    @Test
    void roundTripPreservesLabelsSummaryAndArtifacts() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        RunManifest stamped = store.write(manifest("run-2", Instant.now()));

        RunManifest loaded = store.read("run-2").orElseThrow();
        assertThat(loaded.labels()).containsEntry("clientRequestId", "cid");
        assertThat(loaded.summaryMetrics()).containsEntry("iteration.p95", 12.5);
        assertThat(loaded.artifacts()).hasSize(1);
        assertThat(loaded.artifacts().get(0).artifactId()).isEqualTo("k6-summary");
        assertThat(loaded.artifacts().get(0).checksumSha256()).isEqualTo("abc");
        assertThat(loaded.checksumSha256()).isEqualTo(stamped.checksumSha256());
    }

    @Test
    void legacyFlatManifestRemainsReadable() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        Files.writeString(tempDir.resolve("legacy.json"), """
                {
                  "runId": "legacy",
                  "templateId": "QF-001",
                  "state": "SUCCEEDED",
                  "startedAt": 1767225600,
                  "endedAt": 1767225660,
                  "durationMs": 60000,
                  "artifacts": []
                }
                """);

        RunManifest loaded = store.read("legacy").orElseThrow();
        assertThat(loaded.templateId()).isEqualTo("QF-001");
        assertThat(loaded.startedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(loaded.endedAt()).isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
    }

    @Test
    void readAllPrefersCanonicalOverLegacyDuplicate() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        store.write(manifest("duplicate", Instant.now()));
        Files.writeString(tempDir.resolve("duplicate.json"), """
                {"runId":"duplicate","templateId":"QF-001","state":"FAILED","startedAt":"2020-01-01T00:00:00Z","artifacts":[]}
                """);

        List<RunManifest> all = store.readAll();
        assertThat(all).filteredOn(m -> m.runId().equals("duplicate")).hasSize(1);
        assertThat(all).filteredOn(m -> m.runId().equals("duplicate"))
                .allMatch(m -> m.state().equals("SUCCEEDED"));
    }

    @Test
    void unsafeRunIdIsRejected() {
        ManifestStore store = new ManifestStore(tempDir);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.runDir("../escape"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RunManifest manifest(String runId, Instant startedAt) {
        return new RunManifest(
                runId, "QF-001", "1.0", "SUCCEEDED",
                startedAt, startedAt.plusSeconds(60), 60_000, "completed",
                Map.of("RATE", "10"),
                Map.of("clientRequestId", "cid"),
                Map.of("postgresMajorVersion", 17, "rowCount", 1_000_000),
                "V1", "baseline",
                Map.of("cpuCores", 4, "memoryMb", 4096),
                "abc123",
                Map.of("iteration.p95", 12.5),
                List.of(new RunManifest.ArtifactEntry("k6-summary", "k6-summary", "application/json",
                        "benchmark/runs/" + runId + "/k6-summary.json", "k6 summary", "abc")),
                null);
    }
}
