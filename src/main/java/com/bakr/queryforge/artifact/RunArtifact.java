package com.bakr.queryforge.artifact;

import java.util.List;

/**
 * Lightweight artifact metadata for a run.
 */
public record RunArtifact(
        String kind,
        String contentType,
        String location,
        String description) {
}
