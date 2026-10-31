package com.bakr.queryforge.experiment;

import com.bakr.queryforge.api.dto.ExperimentRunRequest;
import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.PlanParser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Phase 2 run orchestration endpoints.
 */
@Validated
@RestController
@RequestMapping("/api/runs")
public class ExperimentRunController {

    private static final Set<String> TERMINAL_STATES = Set.of(
            "SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT");

    private final ExperimentRunService runs;
    private final ArtifactService artifactService;
    private final RunEventBus events;

    public ExperimentRunController(ExperimentRunService runs,
                                   ArtifactService artifactService,
                                   RunEventBus events) {
        this.runs = runs;
        this.artifactService = artifactService;
        this.events = events;
    }

    @PostMapping
    public ResponseEntity<ExperimentRun> create(
            @Valid @RequestBody ExperimentRunRequest request,
            @RequestHeader(value = "X-Client-Request-Id", required = false) String requestIdHeader) {
        String clientRequestId = request.clientRequestId() != null && !request.clientRequestId().isBlank()
                ? request.clientRequestId()
                : requestIdHeader;
        ExperimentRun created = runs.create(request.templateId(), request.parameters(), clientRequestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ExperimentRun> start(@PathVariable String id) {
        ExperimentRun run = runs.findById(id)
                .orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException("run not found: " + id));
        if (!"QUEUED".equals(run.state())) {
            throw new RunConflictException("run is not queued: " + run.state());
        }
        ExperimentRun started = runs.start(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(started);
    }

    @GetMapping("/{id}/events")
    public SseEmitter events(@PathVariable String id) {
        ExperimentRun run = runs.findById(id)
                .orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException("run not found: " + id));

        SseEmitter emitter = events.subscribe(id);
        try {
            emitter.send(SseEmitter.event().name("state").data(Map.of(
                    "state", run.state(),
                    "at", Instant.now().toString())));
        } catch (Exception ignored) {
            emitter.complete();
            return emitter;
        }
        if (TERMINAL_STATES.contains(run.state())) {
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        ExperimentRun run = runs.findById(id)
                .orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException("run not found: " + id));
        if (TERMINAL_STATES.contains(run.state())) {
            throw new RunConflictException("run already in terminal state: " + run.state());
        }
        boolean cancelled = runs.cancel(id);
        if (!cancelled) {
            throw new RunConflictException("run is not cancellable: " + run.state());
        }
        String state = runs.findById(id).map(ExperimentRun::state).orElse("CANCELLING");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "id", id,
                "state", state
        ));
    }

    /**
     * Run-scoped parsed plan.  Legacy runs publish a plan artifact whose stored
     * path points at the shared {@code docs/experiments/plans} catalog, while
     * new runs carry their own {@code explain-plan} artifact.  Resolving
     * through the run keeps both forms working without guessing a suffix.
     */
    @GetMapping("/{id}/artifacts/{artifactId}/plan")
    public ResponseEntity<PlanParser.PlanNode> parsedPlan(
            @PathVariable String id, @PathVariable String artifactId) {
        runs.findById(id).orElseThrow(
                () -> new com.bakr.queryforge.service.NotFoundException("run not found: " + id));
        Path path = artifactService.resolveForRun(id, artifactId)
                .orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException(
                        "artifact not found: " + artifactId));
        String text = artifactService.readText(path)
                .orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException(
                        "artifact is empty: " + artifactId));
        return ResponseEntity.ok(new PlanParser().parse(text));
    }

    @GetMapping("/{id}/artifacts/{artifactId}")
    public ResponseEntity<Resource> artifact(@PathVariable String id, @PathVariable String artifactId) {
        runs.findById(id).orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException("run not found: " + id));
        Path path = artifactService.resolveForRun(id, artifactId)
                .orElseThrow(() -> new com.bakr.queryforge.service.NotFoundException("artifact not found: " + artifactId));
        Resource resource = new org.springframework.core.io.FileSystemResource(path);
        String contentType = artifactService.guessContentType(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
