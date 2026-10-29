package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunManifestTest {

    @Test
    void recordHoldsAllFields() {
        RunManifest manifest = new RunManifest(
                "run-1", "QF-001", ExperimentRunState.SUCCEEDED, "client-1",
                Map.of("RATE", "10"), "fp-1",
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z"),
                3600000L, "completed",
                List.of(new RunArtifact("k6-summary", "application/json", "p", "d")),
                "sha256hash",
                "V1", "baseline",
                Map.of("cpuCores", 4, "memoryMb", 4096),
                "abc123"
        );
        assertThat(manifest.runId()).isEqualTo("run-1");
        assertThat(manifest.templateId()).isEqualTo("QF-001");
        assertThat(manifest.state()).isEqualTo(ExperimentRunState.SUCCEEDED);
        assertThat(manifest.parameters()).containsEntry("RATE", "10");
        assertThat(manifest.artifacts()).hasSize(1);
        assertThat(manifest.checksumSha256()).isEqualTo("sha256hash");
        assertThat(manifest.migrationVersion()).isEqualTo("V1");
        assertThat(manifest.indexState()).isEqualTo("baseline");
        assertThat(manifest.appCommit()).isEqualTo("abc123");
    }
}
