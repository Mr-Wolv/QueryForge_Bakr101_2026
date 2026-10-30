package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                    String plan = "";
                    try {
                        plan = artifactService.readPlan(id + "-explain");
                    } catch (Exception e) {
                        // no plan available
                    }
                    List<HintService.Hint> hints = hintService.generate(plan, run.metrics());
                    return ResponseEntity.ok(hints);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
