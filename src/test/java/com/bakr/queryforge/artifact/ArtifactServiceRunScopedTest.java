package com.bakr.queryforge.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactServiceRunScopedTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyArtifactsListedInTheRunManifest() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        String runId = "qf001-test-1";
        Path runDir = store.runDir(runId);
        Files.createDirectories(runDir);
        Path summary = runDir.resolve("k6-summary.json");
        Files.writeString(summary, "{\"ok\":true}");

        store.write(manifest(runId, summary));

        ArtifactService service = new ArtifactService(store);
        assertThat(service.resolveForRun(runId, "k6-summary")).contains(summary.toAbsolutePath().normalize());
        assertThat(service.resolveForRun(runId, "manifest"))
                .contains(store.manifestPath(runId).toAbsolutePath().normalize());
        assertThat(service.resolveForRun(runId, "not-listed")).isEmpty();
    }

    @Test
    void rejectsPathTraversalArtifactIds() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        String runId = "qf001-test-2";
        Files.createDirectories(store.runDir(runId));
        store.write(manifest(runId, null));

        ArtifactService service = new ArtifactService(store);
        assertThat(service.resolveForRun(runId, "../manifest")).isEmpty();
        assertThat(service.resolveForRun(runId, "..\\manifest")).isEmpty();
        assertThat(service.resolveForRun(runId, "a/b")).isEmpty();
    }

    @Test
    void manifestCannotPointOutsideAllowedRoots() throws Exception {
        ManifestStore store = new ManifestStore(tempDir);
        String runId = "qf001-test-3";
        Files.createDirectories(store.runDir(runId));
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "secret");

        store.write(manifest(runId, secret));

        ArtifactService service = new ArtifactService(store);
        assertThat(service.resolveForRun(runId, "secret")).isEmpty();
    }

    private static RunManifest manifest(String runId, Path artifact) {
        List<RunManifest.ArtifactEntry> artifacts = artifact == null ? List.of()
                : List.of(new RunManifest.ArtifactEntry(
                ManifestStore.stem(artifact.toString()), "k6-summary", "application/json",
                artifact.toString(), "summary", "abc"));
        return new RunManifest(
                runId, "QF-001", "1.0", "SUCCEEDED",
                Instant.now(), Instant.now(), 1000, "completed",
                Map.of(), Map.of(), Map.of(), "V1", "baseline",
                Map.of(), "unknown", Map.of(), artifacts, null);
    }
}
