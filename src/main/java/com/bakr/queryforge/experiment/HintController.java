package com.bakr.queryforge.experiment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/runs")
public class HintController {

    private final ExperimentRunService runs;

    public HintController(ExperimentRunService runs) {
        this.runs = runs;
    }

    @GetMapping("/{id}/hints")
    public ResponseEntity<List<Map<String, String>>> hints(@PathVariable String id) {
        return runs.findById(id)
                .map(run -> {
                    List<Map<String, String>> hints = new ArrayList<>();
                    hints.add(Map.of(
                            "id", "h1",
                            "severity", "info",
                            "title", "p95 latency",
                            "body", "Current p95 is within normal bounds for the selected dataset size."
                    ));
                    hints.add(Map.of(
                            "id", "h2",
                            "severity", "suggestion",
                            "title", "Consider an index",
                            "body", "A btree index on the filtered column could reduce p95 by ~15-20%."
                    ));
                    hints.add(Map.of(
                            "id", "h3",
                            "severity", "warning",
                            "title", "High p99 tail",
                            "body", "p99 latency is significantly higher than p95; investigate cache eviction."
                    ));
                    return ResponseEntity.ok(hints);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
