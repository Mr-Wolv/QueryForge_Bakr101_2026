package com.bakr.queryforge.experiment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<ExperimentTemplate> get(@PathVariable String id) {
        return templates.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
