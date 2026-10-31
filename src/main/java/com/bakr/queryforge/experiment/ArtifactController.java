package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.PlanParser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private final ArtifactService artifacts;

    public ArtifactController(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping("/{artifactId}")
    public ResponseEntity<Resource> download(@PathVariable String artifactId) {
        // DEPRECATED: Use GET /api/runs/{runId}/artifacts/{artifactId} instead.
        // Global artifact access bypasses run-scoped authorization checks.
        Path path = artifacts.resolve(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found"));
        Resource resource = new org.springframework.core.io.FileSystemResource(path);
        String contentType = artifacts.guessContentType(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .header("Deprecation", "true")
                .header("Link", "</api/runs/{runId}/artifacts/{artifactId}>; rel=\"successor-version\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @GetMapping("/{artifactId}/meta")
    public Map<String, String> meta(@PathVariable String artifactId) {
        // DEPRECATED: Use run-scoped endpoints instead.
        return artifacts.metadata(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found"));
    }

    @GetMapping("/{artifactId}/plan")
    public ResponseEntity<PlanParser.PlanNode> parsedPlan(@PathVariable String artifactId) {
        // DEPRECATED: Use GET /api/runs/{runId}/artifacts/{artifactId}/plan instead.
        Path path = artifacts.resolve(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found"));
        try {
            String text = artifacts.readText(path)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "empty artifact"));
            PlanParser parser = new PlanParser();
            PlanParser.PlanNode root = parser.parse(text);
            return ResponseEntity.ok()
                    .header("Deprecation", "true")
                    .header("Link", "</api/runs/{runId}/artifacts/{artifactId}/plan>; rel=\"successor-version\"")
                    .body(root);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to parse plan: " + e.getMessage());
        }
    }
}
