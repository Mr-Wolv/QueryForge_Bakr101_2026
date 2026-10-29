package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunHistoryController {

    private final ExperimentRunService runs;
    private final ExportService exportService;

    public RunHistoryController(ExperimentRunService runs, ExportService exportService) {
        this.runs = runs;
        this.exportService = exportService;
    }

    @GetMapping
    public List<ExperimentRun> list() {
        return runs.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExperimentRun> get(@PathVariable String id) {
        return runs.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/template/{templateId}")
    public List<ExperimentRun> listByTemplate(@PathVariable String templateId) {
        return runs.findByTemplate(templateId);
    }

    @GetMapping("/{id}/artifacts")
    public ResponseEntity<List<RunArtifact>> artifacts(@PathVariable String id) {
        return runs.findById(id)
                .map(r -> ResponseEntity.ok(r.artifacts()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable String id,
                                         @RequestParam(defaultValue = "markdown") String format) {
        try {
            if ("html".equalsIgnoreCase(format)) {
                String html = exportService.exportHtml(id);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + id + ".html\"")
                        .header("Content-Type", "text/html; charset=utf-8")
                        .body(html);
            } else if ("markdown".equalsIgnoreCase(format)) {
                String md = exportService.exportMarkdown(id);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + id + ".md\"")
                        .header("Content-Type", "text/markdown; charset=utf-8")
                        .body(md);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported format: " + format + ". Use markdown or html.");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
