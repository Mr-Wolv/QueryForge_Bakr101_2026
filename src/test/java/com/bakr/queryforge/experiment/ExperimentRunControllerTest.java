package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentRunControllerUnitTest {

    private final ExperimentRunService runs = Mockito.mock(ExperimentRunService.class);
    private final ArtifactService artifactService = new ArtifactService();
    private final ExperimentRunController controller =
            new ExperimentRunController(runs, artifactService, new RunEventBus());

    @Test
    void createReturnsCreated() {
        ExperimentRun created = run("run-1", "QUEUED");
        Mockito.when(runs.create(Mockito.eq("QF-001"), Mockito.any(), Mockito.eq("cid")))
                .thenReturn(created);

        ResponseEntity<ExperimentRun> resp = controller.create(
                new com.bakr.queryforge.api.dto.ExperimentRunRequest(
                        "QF-001", Map.of("RATE", "10"), "cid"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isEqualTo(created);
    }

    @Test
    void createUsesClientRequestIdHeaderWhenBodyOmitsIt() {
        ExperimentRun created = run("run-1", "QUEUED");
        Mockito.when(runs.create(Mockito.eq("QF-001"), Mockito.any(), Mockito.eq("header-id")))
                .thenReturn(created);

        controller.create(new com.bakr.queryforge.api.dto.ExperimentRunRequest(
                "QF-001", Map.of("RATE", "10"), null), "header-id");

        Mockito.verify(runs).create("QF-001", Map.of("RATE", "10"), "header-id");
    }

    @Test
    void startReturnsAcceptedWhenQueued() {
        ExperimentRun queued = run("run-1", "QUEUED");
        ExperimentRun started = run("run-1", "RUNNING");
        Mockito.when(runs.findById("run-1")).thenReturn(Optional.of(queued));
        Mockito.when(runs.start("run-1")).thenReturn(started);

        ResponseEntity<ExperimentRun> resp = controller.start("run-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody().state()).isEqualTo("RUNNING");
    }

    @Test
    void startReturnsConflictWhenNotQueued() {
        Mockito.when(runs.findById("run-1")).thenReturn(Optional.of(run("run-1", "RUNNING")));

        assertThatThrownBy(() -> controller.start("run-1"))
                .isInstanceOf(RunConflictException.class);
    }

    @Test
    void cancelReturnsAcceptedWithCancellingState() {
        ExperimentRun running = run("run-1", "RUNNING");
        ExperimentRun cancelling = run("run-1", "CANCELLING");
        Mockito.when(runs.findById("run-1"))
                .thenReturn(Optional.of(running), Optional.of(cancelling));
        Mockito.when(runs.cancel("run-1")).thenReturn(true);

        ResponseEntity<Map<String, Object>> resp = controller.cancel("run-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody()).containsEntry("state", "CANCELLING");
    }

    @Test
    void cancelReturnsConflictWhenTerminal() {
        Mockito.when(runs.findById("run-1")).thenReturn(Optional.of(run("run-1", "SUCCEEDED")));

        assertThatThrownBy(() -> controller.cancel("run-1"))
                .isInstanceOf(RunConflictException.class);
    }

    private static ExperimentRun run(String id, String state) {
        return new ExperimentRun(id, "QF-001", state, Instant.now(), 0,
                Map.of(), Map.of(), com.bakr.queryforge.measurement.Metrics.empty(),
                List.of(), "unknown");
    }
}
