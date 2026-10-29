package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade for run lifecycle operations.
 *
 * <p>Reads continue to come from the artifact-backed loader; writes go through
 * the orchestrator.
 */
@Service
public class ExperimentRunService {

    private final ExperimentTemplateService templates;
    private final ExperimentOrchestrator orchestrator;

    public ExperimentRunService(ExperimentTemplateService templates, ExperimentOrchestrator orchestrator) {
        this.templates = templates;
        this.orchestrator = orchestrator;
    }

    public List<ExperimentRun> findAll() {
        List<ExperimentRun> fromOrchestrator = orchestrator.loadAll();
        if (!fromOrchestrator.isEmpty()) return fromOrchestrator;
        return templates.loadRuns();
    }

    public Optional<ExperimentRun> findById(String id) {
        return orchestrator.findById(id)
                .or(() -> templates.loadRuns().stream()
                        .filter(r -> r.id().equals(id))
                        .findFirst());
    }

    public List<ExperimentRun> findByTemplate(String templateId) {
        return findAll().stream()
                .filter(r -> r.templateId().equals(templateId))
                .toList();
    }

    public ExperimentRun create(String templateId, Map<String, String> parameters, String clientRequestId) {
        return orchestrator.createRun(templateId, parameters, clientRequestId);
    }
}
