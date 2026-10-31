package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.ManifestStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HintControllerTest {

    @TempDir
    Path tempDir;

    private HintController c;
    private ExperimentRunService runs;

    private void setup() {
        ManifestStore manifests = new ManifestStore(tempDir);
        ExperimentOrchestrator orchestrator = new ExperimentOrchestrator(
                new ExperimentTemplateService(), manifests,
                new EnvironmentFingerprintService(), new RunEventBus(), List.of());
        runs = new ExperimentRunService(new ExperimentTemplateService(), orchestrator, null);
        ArtifactService artifactService = new ArtifactService();
        HintService hintService = new HintService(artifactService);
        c = new HintController(runs, artifactService, hintService);
    }

    @Test
    void hintsReturnsNotFoundForUnknownRun() {
        setup();
        ResponseEntity<List<HintService.Hint>> response = c.hints("nonexistent");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void hintsReturnsEmptyForRunWithoutArtifacts() {
        setup();
        String runId = runs.create("QF-001", Map.of(
                "RATE", "10", "DUR", "60", "OUT", "hint-run"
        ), "client-hint").id();

        ResponseEntity<List<HintService.Hint>> response = c.hints(runId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }
}
