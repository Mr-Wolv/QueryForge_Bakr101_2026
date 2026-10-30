package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;

/**
 * Lightweight artifact metadata for a run.
 *
 * <p>{@code artifactId} is the path-safe identifier used by
 * {@code /api/runs/{id}/artifacts/{artifactId}}. The physical location is
 * deliberately hidden from API responses so clients cannot probe the filesystem.
 * The four-argument constructor derives the ID from the file name for
 * historical callers.
 */
public record RunArtifact(
        String artifactId,
        String kind,
        String contentType,
        @JsonIgnore String location,
        String description) {

    public RunArtifact(String kind, String contentType, String location, String description) {
        this(ManifestStore.stem(location), kind, contentType, location, description);
    }

    public static RunArtifact of(String kind, String contentType, Path location, String description) {
        return new RunArtifact(kind, contentType, location.toString(), description);
    }
}
