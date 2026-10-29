package com.bakr.queryforge.experiment;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentRunControllerUnitTest {

    @Test
    void createReturnsAccepted() {
        ExperimentRunService runs = Mockito.mock(ExperimentRunService.class);
        ExperimentTemplateService templates = Mockito.mock(ExperimentTemplateService.class);
        ExperimentOrchestrator orchestrator = Mockito.mock(ExperimentOrchestrator.class);
        ExperimentRunController controller = new ExperimentRunController(runs, templates, orchestrator);

        ExperimentRun created = new ExperimentRun("run-1", "QF-001", "QUEUED",
                java.time.Instant.now(), 0, Map.of(),
                com.bakr.queryforge.measurement.Metrics.empty(), List.of());
        Mockito.when(runs.create(Mockito.eq("QF-001"), Mockito.any(), Mockito.anyString()))
                .thenReturn(created);

        ResponseEntity<ExperimentRun> resp = controller.create(new com.bakr.queryforge.api.dto.ExperimentRunRequest("QF-001", Map.of("RATE", "10"), "cid"));

        assertThat(resp.getStatusCodeValue()).isEqualTo(202);
        assertThat(resp.getBody()).isEqualTo(created);
    }

    @Test
    void startReturnsOkWhenQueued() throws Exception {
        ExperimentRunService runs = Mockito.mock(ExperimentRunService.class);
        ExperimentTemplateService templates = Mockito.mock(ExperimentTemplateService.class);
        ExperimentOrchestrator orchestrator = Mockito.mock(ExperimentOrchestrator.class);
        ExperimentRunController controller = new ExperimentRunController(runs, templates, orchestrator);

        ExperimentRun queued = new ExperimentRun("run-1", "QF-001", "QUEUED",
                java.time.Instant.now(), 0, Map.of("clientRequestId", "cid"),
                com.bakr.queryforge.measurement.Metrics.empty(), List.of());
        Mockito.when(runs.findById("run-1")).thenReturn(java.util.Optional.of(queued));
        Mockito.when(templates.findById("QF-001"))
                .thenReturn(java.util.Optional.of(new ExperimentTemplate("QF-001", "t", "p", "h", List.of(), "1m", false, List.of(), "", List.of())));
        ExperimentRun started = new ExperimentRun("run-1", "QF-001", "RUNNING",
                queued.startedAt(), 0, Map.of(),
                com.bakr.queryforge.measurement.Metrics.empty(), List.of());
        Mockito.when(orchestrator.run(Mockito.eq("run-1"), Mockito.any(), Mockito.any()))
                .thenReturn(started);

        ResponseEntity<ExperimentRun> resp = controller.start("run-1");

        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody().state()).isEqualTo("RUNNING");
    }

    @Test
    void startReturnsConflictWhenNotQueued() {
        ExperimentRunService runs = Mockito.mock(ExperimentRunService.class);
        ExperimentTemplateService templates = Mockito.mock(ExperimentTemplateService.class);
        ExperimentOrchestrator orchestrator = Mockito.mock(ExperimentOrchestrator.class);
        ExperimentRunController controller = new ExperimentRunController(runs, templates, orchestrator);

        ExperimentRun running = new ExperimentRun("run-1", "QF-001", "RUNNING",
                java.time.Instant.now(), 0, Map.of(),
                com.bakr.queryforge.measurement.Metrics.empty(), List.of());
        Mockito.when(runs.findById("run-1")).thenReturn(java.util.Optional.of(running));

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.start("run-1"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
