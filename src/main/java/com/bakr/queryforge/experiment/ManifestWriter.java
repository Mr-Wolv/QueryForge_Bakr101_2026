package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ManifestWriter {

    private final ManifestStore manifests;
    private final EnvironmentFingerprintService fingerprints;

    public ManifestWriter(ManifestStore manifests, EnvironmentFingerprintService fingerprints) {
        this.manifests = manifests;
        this.fingerprints = fingerprints;
    }

    public RunManifest write(ExperimentRun run, String outcome,
                             EnvironmentFingerprintService.Snapshot fingerprint, Instant endedAt) {
        List<RunManifest.ArtifactEntry> artifacts = run.artifacts().stream().map(artifact ->
                new RunManifest.ArtifactEntry(
                        artifact.artifactId(), artifact.kind(), artifact.contentType(),
                        artifact.location(), artifact.description(), checksumOf(artifact.location()))
        ).toList();

        RunManifest manifest = new RunManifest(
                run.id(),
                run.templateId(),
                "1.0",
                run.state(),
                run.startedAt(),
                endedAt,
                run.durationMs(),
                outcome,
                run.parameters(),
                run.labels(),
                fingerprints.toMap(fingerprint),
                "V1",
                fingerprint.indexState(),
                Map.of("cpuCores", fingerprint.cpuCores(), "memoryMb", fingerprint.memoryMb()),
                run.appCommit(),
                summaryMetrics(run),
                artifacts,
                null
        );
        try {
            return manifests.write(manifest);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to write manifest for " + run.id() + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> summaryMetrics(ExperimentRun run) {
        Map<String, Object> summary = new LinkedHashMap<>();
        run.metrics().iterationPercentiles().forEach((key, value) ->
                summary.put("iteration." + key, value));
        run.metrics().httpPercentiles().forEach((key, value) ->
                summary.put("http." + key, value));
        summary.put("iterations", run.metrics().iterationCount());
        summary.put("rps", run.metrics().rps());
        summary.put("testRunDurationMs", run.metrics().testRunDurationMs());
        return summary;
    }

    private static String checksumOf(String location) {
        try {
            Path path = Path.of(location);
            if (!Files.isRegularFile(path)) return "unknown";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
