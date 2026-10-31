package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import com.bakr.queryforge.service.PageResponse;
import org.springframework.stereotype.Service;

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
            return persistence.findPage(state, page, size);
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

    public Optional<ExperimentRun> findById(String id) {
        if (persistence != null) {
            return persistence.findById(id)
                    .or(() -> java.util.Optional.empty());
        }
        return orchestrator.findById(id)
                .or(() -> templates.loadRuns().stream()
                        .filter(r -> r.id().equals(id))
                        .findFirst());
    }

    public PageResponse<ExperimentRun> findByTemplate(String templateId, int page, int size) {
        if (persistence != null) {
            int safePage = Math.max(0, page);
            int safeSize = Math.min(100, Math.max(1, size));
            var all = persistence.findByTemplate(templateId);
            long total = all.size();
            int from = Math.min(safePage * safeSize, all.size());
            int to = Math.min(from + safeSize, all.size());
            List<ExperimentRun> content = all.subList(from, to);
            return PageResponse.of(content, safePage, safeSize, total);
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
        return findById(runId)
                .map(run -> run.artifacts())
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
