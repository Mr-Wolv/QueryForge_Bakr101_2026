package com.bakr.queryforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA-backed persistence for experiment runs.
 *
 * <p>This is the first increment of the DB-backed runs store (Phase 5). The
 * {@link com.bakr.queryforge.experiment.ExperimentRun} record remains the
 * API-facing type; this entity is the durable backing store. Conversion between
 * the two happens in {@link com.bakr.queryforge.experiment.RunStore}.
 */
@Entity
@Table(name = "experiment_runs")
public class ExperimentRunEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "template_id", nullable = false, length = 128)
    private String templateId;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "labels", columnDefinition = "TEXT")
    private String labelsJson;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "metrics", columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "app_commit", length = 64)
    private String appCommit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ExperimentRunEntity() {
        this.id = UUID.randomUUID().toString();
    }

    public ExperimentRunEntity(String id) {
        this.id = id;
    }

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getLabelsJson() { return labelsJson; }
    public void setLabelsJson(String labelsJson) { this.labelsJson = labelsJson; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getAppCommit() { return appCommit; }
    public void setAppCommit(String appCommit) { this.appCommit = appCommit; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
