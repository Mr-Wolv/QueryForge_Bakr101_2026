package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import com.bakr.queryforge.service.PageResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade for run lifecycle operations.
 *
 * <p>Manifest-backed runs are merged with the historical {@code benchmark/results}
 * catalog so evidence recorded before the orchestration layer existed remains
 * visible. Writes always go through the orchestrator.
 */
@Service
public class ExperimentRunService {

    private final ExperimentTemplateService templates;
    private final ExperimentOrchestrator orchestrator;
    private final RunPersistenceService persistence;

    public ExperimentRunService(ExperimentTemplateService templates, ExperimentOrchestrator orchestrator, RunPersistenceService persistence) {
        this.templates = templates;
        this.orchestrator = orchestrator;
        this.persistence = persistence;
    }

    public PageResponse<ExperimentRun> findPage(String state, int page, int size) {
        if (persistence != null) {
            return persistence.findPage(state, null, null, null, page, size, "startedAt", "desc");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<ExperimentRun> all = orchestrator.loadAll(safePage, safeSize);
        if (state != null && !state.isBlank()) {
            all = all.stream().filter(r -> state.equalsIgnoreCase(r.state())).toList();
        }
        long total = orchestrator.countAll();
        int from = safePage * safeSize;
        List<ExperimentRun> content = from >= total
                ? List.of()
                : all;
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public PageResponse<ExperimentRun> findPage(
            String state, String templateId, Instant from, Instant toExclusive,
            int page, int size, String sortBy, String sortDirection) {
        if (persistence == null) {
            return findPage(state, page, size);
        }
        return persistence.findPage(
                state, templateId, from, toExclusive, page, size, sortBy, sortDirection);
    }

    public Optional<ExperimentRun> findById(String id) {
        if (persistence != null) {
            return persistence.findById(id);
        }
        return orchestrator.findById(id)
                .or(() -> templates.loadRuns().stream()
                        .filter(r -> r.id().equals(id))
                        .findFirst());
    }

    public PageResponse<ExperimentRun> findByTemplate(String templateId, int page, int size) {
        if (persistence != null) {
            return persistence.findPage(
                    null, templateId, null, null, page, size, "startedAt", "desc");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<ExperimentRun> all = orchestrator.loadAll(safePage, safeSize)
                .stream()
                .filter(r -> templateId.equals(r.templateId()))
                .toList();
        long total = orchestrator.countAll();
        int from = safePage * safeSize;
        List<ExperimentRun> content = from >= total
                ? List.of()
                : all;
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public Optional<RunManifest> manifest(String runId) {
        return orchestrator.manifest(runId);
    }

    public List<RunArtifact> artifacts(String runId) {
        List<RunArtifact> recorded = findById(runId)
                .map(ExperimentRun::artifacts)
                .orElse(List.of());
        if (!recorded.isEmpty()) return recorded;
        // Runs recorded before artifact persistence carry only the manifest. The manifest is
        // the record of truth, so surface its entries instead of reporting no artifacts.
        return manifest(runId)
                .map(m -> m.artifacts().stream()
                        .map(e -> new RunArtifact(e.artifactId(), e.kind(), e.contentType(),
                                e.location(), e.description()))
                        .toList())
                .orElse(List.of());
    }

    public ExperimentRun create(String templateId, Map<String, String> parameters, String clientRequestId) {
        ExperimentRun run = orchestrator.createRun(templateId, parameters, clientRequestId);
        if (persistence != null) persistence.save(run);
        return run;
    }

    public ExperimentRun start(String runId) {
        return orchestrator.startAsync(runId);
    }

    public boolean cancel(String runId) {
        return orchestrator.cancel(runId);
    }
}
