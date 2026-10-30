package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/runs")
public class HintController {

    private final ExperimentRunService runs;
    private final ArtifactService artifactService;
    private final HintService hintService;

    public HintController(ExperimentRunService runs, ArtifactService artifactService, HintService hintService) {
        this.runs = runs;
        this.artifactService = artifactService;
        this.hintService = hintService;
    }

    @GetMapping("/{id}/hints")
    public ResponseEntity<List<HintService.Hint>> hints(@PathVariable String id) {
        return runs.findById(id)
                .map(run -> {
                    String plan = planText(id, run.artifacts());
                    List<HintService.Hint> hints = hintService.generate(plan, run.metrics());
                    return ResponseEntity.ok(hints);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Read the run's own explain-plan artifact, falling back to the legacy catalog. */
    private String planText(String runId, List<RunArtifact> artifacts) {
        for (RunArtifact artifact : artifacts) {
            if ("explain-plan".equals(artifact.kind())) {
                Optional<Path> path = artifactService.resolveForRun(runId, artifact.artifactId());
                if (path.isPresent()) {
                    return artifactService.readText(path.get()).orElse("");
                }
            }
        }
        try {
            return artifactService.readPlan(runId + "-explain");
        } catch (Exception e) {
            return "";
        }
    }
}
