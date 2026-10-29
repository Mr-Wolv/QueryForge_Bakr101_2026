package com.bakr.queryforge.experiment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Comparison API endpoints.
 */
@RestController
@RequestMapping("/api/comparisons")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final ExperimentRunService runs;

    public ComparisonController(ComparisonService comparisonService, ExperimentRunService runs) {
        this.comparisonService = comparisonService;
        this.runs = runs;
    }

    public record CompareRequest(String leftRunId, String rightRunId, String createdBy) {}

    @PostMapping
    public ResponseEntity<?> compare(@RequestBody(required = false) Map<String, String> body) {
        String a, b, createdBy;
        if (body != null && body.containsKey("leftRunId")) {
            a = body.get("leftRunId");
            b = body.get("rightRunId");
            createdBy = body.get("createdBy");
        } else if (body != null && body.containsKey("a")) {
            a = body.get("a");
            b = body.get("b");
            createdBy = null;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leftRunId and rightRunId are required");
        }
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "both run ids are required"));
        }
        if (a.equals(b)) {
            return ResponseEntity.badRequest().body(Map.of("error", "run ids must differ"));
        }
        try {
            ComparisonReport report = comparisonService.compare(a, b, createdBy);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComparisonReport> get(@PathVariable String id) {
        return comparisonService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<ComparisonReport> list() {
        return comparisonService.loadAll();
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable String id,
                                         @RequestParam(defaultValue = "markdown") String format) {
        ComparisonReport report = comparisonService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "comparison not found: " + id));
        String content;
        String contentType;
        if ("html".equalsIgnoreCase(format)) {
            ExperimentRun left = runs.findById(report.leftRunId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "left run not found"));
            ExperimentRun right = runs.findById(report.rightRunId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "right run not found"));
            content = comparisonService.buildHtml(left, right,
                    report.verdict().compatible(), report.verdict().blockers(),
                    report.verdict().warnings(), report.verdict().diff());
            contentType = "text/html; charset=utf-8";
        } else if ("markdown".equalsIgnoreCase(format)) {
            content = report.content();
            contentType = "text/markdown; charset=utf-8";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported format: " + format + ". Use markdown or html.");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + id + "." + format + "\"")
                .header("Content-Type", contentType)
                .body(content);
    }
}
