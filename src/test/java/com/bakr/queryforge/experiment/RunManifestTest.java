package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunManifest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunManifestTest {

    @Test
    void recordHoldsAllFields() {
        RunManifest manifest = new RunManifest(
                "run-1", "QF-001", "1.0", "SUCCEEDED",
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z"),
                3600000L, "completed",
                Map.of("RATE", "10"),
                Map.of("clientRequestId", "cid"),
                Map.of("postgresMajorVersion", 17, "rowCount", 1_000_000),
                "V1", "baseline",
                Map.of("cpuCores", 4, "memoryMb", 4096),
                "abc123",
                Map.of("iteration.p95", 12.5),
                List.of(new RunManifest.ArtifactEntry("manifest", "manifest", "application/json",
                        "manifest.json", "Run manifest", "sha")),
                "sha256hash"
        );
        assertThat(manifest.runId()).isEqualTo("run-1");
        assertThat(manifest.templateId()).isEqualTo("QF-001");
        assertThat(manifest.templateVersion()).isEqualTo("1.0");
        assertThat(manifest.state()).isEqualTo("SUCCEEDED");
        assertThat(manifest.parameters()).containsEntry("RATE", "10");
        assertThat(manifest.labels()).containsEntry("clientRequestId", "cid");
        assertThat(manifest.environmentFingerprint()).containsEntry("postgresMajorVersion", 17);
        assertThat(manifest.summaryMetrics()).containsEntry("iteration.p95", 12.5);
        assertThat(manifest.checksumSha256()).isEqualTo("sha256hash");
        assertThat(manifest.migrationVersion()).isEqualTo("V1");
        assertThat(manifest.indexState()).isEqualTo("baseline");
        assertThat(manifest.appCommit()).isEqualTo("abc123");
    }
}
