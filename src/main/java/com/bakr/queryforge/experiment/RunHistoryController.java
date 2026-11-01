package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.service.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/runs")
public class RunHistoryController {

    private final ExperimentRunService runs;
    private final ExportService exportService;
    private final ArtifactService artifactService;

    public RunHistoryController(ExperimentRunService runs, ExportService exportService,
                                ArtifactService artifactService) {
        this.runs = runs;
        this.exportService = exportService;
        this.artifactService = artifactService;
    }

    @GetMapping
    public PageResponse<ExperimentRun> list(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String templateId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' date must not be after 'to'");
        }
        return runs.findPage(
                state,
                templateId,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                page,
                size,
                sortBy,
                sortDirection);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExperimentRun> get(@PathVariable String id) {
        return runs.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/template/{templateId}")
    public PageResponse<ExperimentRun> listByTemplate(@PathVariable String templateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return runs.findByTemplate(templateId, page, size);
    }

    @GetMapping("/{id}/artifacts")
    public ResponseEntity<List<RunArtifact>> artifacts(@PathVariable String id) {
        return runs.findById(id)
                .map(r -> ResponseEntity.ok(artifactService.catalogForRun(id, runs.artifacts(id))))
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
