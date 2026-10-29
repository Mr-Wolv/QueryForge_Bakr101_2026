package com.bakr.queryforge.experiment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentCatalogController {

    private final ExperimentTemplateService templates;

    public ExperimentCatalogController(ExperimentTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    public List<ExperimentTemplate> list() {
        return templates.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return templates.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "experiment not found: " + id)));
    }
}
