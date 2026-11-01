package com.bakr.queryforge.experiment;

import com.bakr.queryforge.entity.ExperimentRunEntity;
import com.bakr.queryforge.measurement.Metrics;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * Converts between the API-facing {@link ExperimentRun} record and the durable
 * {@link ExperimentRunEntity}. Maps/collections are serialized as JSON.
 */
public final class ExperimentRunMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExperimentRunMapper() {}

    public static ExperimentRunEntity toEntity(ExperimentRun run) {
        return updateEntity(run, new ExperimentRunEntity(run.id()));
    }

    public static ExperimentRunEntity updateEntity(ExperimentRun run, ExperimentRunEntity e) {
        e.setTemplateId(run.templateId());
        e.setState(run.state());
        e.setStartedAt(run.startedAt());
        e.setDurationMs(run.durationMs());
        e.setAppCommit(run.appCommit());
        try {
            e.setLabelsJson(MAPPER.writeValueAsString(run.labels()));
            e.setParametersJson(MAPPER.writeValueAsString(run.parameters()));
            e.setMetricsJson(MAPPER.writeValueAsString(run.metrics()));
            e.setArtifactsJson(MAPPER.writeValueAsString(run.artifacts()));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize run " + run.id(), ex);
        }
        return e;
    }

    public static ExperimentRun toRecord(ExperimentRunEntity e) {
        Map<String, String> labels = Map.of();
        Map<String, String> params = Map.of();
        Metrics metrics = null;
        java.util.List<RunArtifact> artifacts = java.util.List.of();
        try {
            if (e.getLabelsJson() != null) labels = MAPPER.readValue(
                    e.getLabelsJson(), new TypeReference<Map<String, String>>() {});
            if (e.getParametersJson() != null) params = MAPPER.readValue(
                    e.getParametersJson(), new TypeReference<Map<String, String>>() {});
            if (e.getMetricsJson() != null) metrics = MAPPER.readValue(e.getMetricsJson(), Metrics.class);
            if (e.getArtifactsJson() != null) artifacts = MAPPER.readValue(
                    e.getArtifactsJson(), new TypeReference<java.util.List<RunArtifact>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize run " + e.getId(), ex);
        }
        return new ExperimentRun(
                e.getId(),
                e.getTemplateId(),
                e.getState(),
                e.getStartedAt(),
                e.getDurationMs(),
                labels,
                params,
                metrics,
                artifacts,
                e.getAppCommit()
        );
    }

    public static ExperimentRun fromManifest(RunManifest manifest) {
        var artifacts = (manifest.artifacts() == null ? java.util.List.<RunManifest.ArtifactEntry>of()
                : manifest.artifacts()).stream()
                .map(artifact -> new RunArtifact(
                        artifact.artifactId(),
                        artifact.kind(),
                        artifact.contentType(),
                        artifact.location(),
                        artifact.description()))
                .toList();
        return new ExperimentRun(
                manifest.runId(),
                manifest.templateId(),
                manifest.state(),
                manifest.startedAt(),
                manifest.durationMs(),
                manifest.labels(),
                manifest.parameters(),
                Metrics.fromSummaryMap(manifest.summaryMetrics()),
                artifacts,
                manifest.appCommit());
    }
}
