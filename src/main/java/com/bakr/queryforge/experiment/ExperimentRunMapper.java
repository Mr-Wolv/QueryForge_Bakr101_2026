package com.bakr.queryforge.experiment;

import com.bakr.queryforge.entity.ExperimentRunEntity;
import com.bakr.queryforge.measurement.Metrics;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Converts between the API-facing {@link ExperimentRun} record and the durable
 * {@link ExperimentRunEntity}. Maps/collections are serialized as JSON.
 */
public final class ExperimentRunMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExperimentRunMapper() {}

    public static ExperimentRunEntity toEntity(ExperimentRun run) {
        ExperimentRunEntity e = new ExperimentRunEntity(run.id());
        e.setTemplateId(run.templateId());
        e.setState(run.state());
        e.setStartedAt(run.startedAt());
        e.setDurationMs(run.durationMs());
        e.setAppCommit(run.appCommit());
        try {
            e.setLabelsJson(MAPPER.writeValueAsString(run.labels()));
            e.setParametersJson(MAPPER.writeValueAsString(run.parameters()));
            e.setMetricsJson(MAPPER.writeValueAsString(run.metrics()));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize run " + run.id(), ex);
        }
        return e;
    }

    @SuppressWarnings("unchecked")
    public static ExperimentRun toRecord(ExperimentRunEntity e) {
        Map<String, String> labels = Map.of();
        Map<String, String> params = Map.of();
        Metrics metrics = null;
        try {
            if (e.getLabelsJson() != null) labels = MAPPER.readValue(e.getLabelsJson(), Map.class);
            if (e.getParametersJson() != null) params = MAPPER.readValue(e.getParametersJson(), Map.class);
            if (e.getMetricsJson() != null) metrics = MAPPER.readValue(e.getMetricsJson(), Metrics.class);
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
                java.util.List.of(),
                e.getAppCommit()
        );
    }
}
