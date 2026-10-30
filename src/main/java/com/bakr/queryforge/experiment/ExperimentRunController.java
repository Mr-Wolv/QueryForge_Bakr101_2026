package com.bakr.queryforge.experiment;

import com.bakr.queryforge.api.dto.ExperimentRunRequest;
import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.experiment.ExperimentTemplate;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 run orchestration endpoints.
 */
@Validated
@RestController
@RequestMapping("/api/runs")
public class ExperimentRunController {

    private final ExperimentRunService runs;
    private final ExperimentTemplateService templates;
    private final ExperimentOrchestrator orchestrator;
    private final ArtifactService artifactService;

    public ExperimentRunController(ExperimentRunService runs, ExperimentTemplateService templates, ExperimentOrchestrator orchestrator, ArtifactService artifactService) {
        this.runs = runs;
        this.templates = templates;
        this.orchestrator = orchestrator;
        this.artifactService = artifactService;
    }

    @PostMapping
    public ResponseEntity<ExperimentRun> create(@Valid @RequestBody ExperimentRunRequest request) {
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
            ExperimentRun started = orchestrator.run(id, template, run.parameters());
            return ResponseEntity.ok(started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "interrupted");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{id}/events")
    public SseEmitter events(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(0L);
        ExperimentRun run = runs.findById(id).orElse(null);
        if (run != null) {
            try {
                emitter.send(SseEmitter.event().name("state").data(Map.of("state", run.state())));
            } catch (Exception ignored) {}
        }
        return emitter;
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

    @GetMapping("/{id}/artifacts/{artifactId}")
    public ResponseEntity<Resource> artifact(@PathVariable String id, @PathVariable String artifactId) {
        runs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));
        Path path = artifactService.resolve(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found"));
        Resource resource = new org.springframework.core.io.FileSystemResource(path);
        String contentType = artifactService.guessContentType(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
