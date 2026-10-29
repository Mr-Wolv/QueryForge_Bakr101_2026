package com.bakr.queryforge.experiment;

import com.bakr.queryforge.experiment.ExperimentTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Phase 2 run orchestration endpoints.
 */
@RestController
@RequestMapping("/api/runs")
public class ExperimentRunController {

    private final ExperimentRunService runs;
    private final ExperimentTemplateService templates;
    private final ExperimentOrchestrator orchestrator;

    public ExperimentRunController(ExperimentRunService runs, ExperimentTemplateService templates, ExperimentOrchestrator orchestrator) {
        this.runs = runs;
        this.templates = templates;
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<ExperimentRun> create(@RequestBody ExperimentRunRequest request) {
        ExperimentRun created = runs.create(request.templateId(), request.parameters(), request.clientRequestId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(created);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ExperimentRun> start(@PathVariable String id) {
        ExperimentRun run = runs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));
        if (!"QUEUED".equals(run.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "run is not queued");
        }
        try {
            ExperimentTemplate template = templates.findById(run.templateId())
                    .orElseThrow(() -> new IllegalStateException("template not found: " + run.templateId()));
            ExperimentRun started = orchestrator.run(id, template, run.labels());
            return ResponseEntity.ok(started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "interrupted");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        ExperimentRun run = runs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));
        String terminalStates = "SUCCEEDED|FAILED|CANCELLED|TIMED_OUT";
        if (terminalStates.contains(run.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "run already in terminal state: " + run.state());
        }
        boolean cancelled = orchestrator.cancel(id);
        if (!cancelled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found or not cancellable");
        }
        return ResponseEntity.ok(Map.of(
                "runId", id,
                "state", "CANCELLED",
                "message", "run cancelled successfully"
        ));
    }
}
