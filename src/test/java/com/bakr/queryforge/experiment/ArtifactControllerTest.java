package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactControllerTest {

    private final ArtifactService service = new ArtifactService();
    private final ArtifactController controller = new ArtifactController(service);

    @Test
    void downloadExistingArtifactReturnsResource() {
        ResponseEntity<Resource> response = controller.download("qf001-baseline");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
    }

    @Test
    void downloadMissingArtifactReturnsNotFound() {
        assertThatThrownBy(() -> controller.download("does-not-exist"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void metaExistingArtifactReturnsMap() {
        Map<String, String> meta = controller.meta("qf001-baseline");
        assertThat(meta).containsKeys("path", "contentType", "sizeBytes");
    }

    @Test
    void metaMissingArtifactThrowsNotFound() {
        assertThatThrownBy(() -> controller.meta("does-not-exist"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void parsedPlanExistingArtifactReturnsPlanNode() {
        ResponseEntity<?> response = controller.parsedPlan("qf001-baseline");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
    }

    @Test
    void parsedPlanMissingArtifactThrowsNotFound() {
        assertThatThrownBy(() -> controller.parsedPlan("does-not-exist"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
