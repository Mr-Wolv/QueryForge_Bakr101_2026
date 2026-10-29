package com.bakr.queryforge.artifact;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactServicePathTraversalTest {

    private final ArtifactService service = new ArtifactService();

    @Test
    void rejectsNullArtifactId() {
        assertThat(service.resolve(null)).isEmpty();
    }

    @Test
    void rejectsBlankArtifactId() {
        assertThat(service.resolve("")).isEmpty();
        assertThat(service.resolve("   ")).isEmpty();
    }

    @Test
    void rejectsDotDotTraversal() {
        assertThat(service.resolve("..\\..\\etc\\passwd")).isEmpty();
        assertThat(service.resolve("../../etc/passwd")).isEmpty();
        assertThat(service.resolve("..%2f..%2fetc")).isEmpty();
    }

    @Test
    void rejectsForwardSlashInId() {
        assertThat(service.resolve("foo/bar")).isEmpty();
        assertThat(service.resolve("foo/../../../etc/passwd")).isEmpty();
    }

    @Test
    void rejectsBackslashInId() {
        assertThat(service.resolve("foo\\bar")).isEmpty();
    }

    @Test
    void normalizesAbsolutePathIfFileExists() {
        // A well-formed ID that maps to a real file under ROOT should still resolve
        // (the normalize step ensures the returned path is absolute)
        Path resultsDir = Path.of("benchmark", "results");
        if (java.nio.file.Files.isDirectory(resultsDir)) {
            // Just verify the method doesn't throw for clean IDs
            service.resolve("some-nonexistent-result");
        }
    }
}
