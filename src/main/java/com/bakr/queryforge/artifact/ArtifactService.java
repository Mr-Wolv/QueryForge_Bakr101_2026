package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves, serves, and parses experiment artifacts.
 *
 * Phase 1 scope: read-only access to benchmark JSON results and plan text files.
 */
@Service
public class ArtifactService {

    private static final Path ROOT = Paths.get("").toAbsolutePath();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int PLAN_CACHE_MAX = 64;
    private static final Map<String, String> PLAN_CACHE = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > PLAN_CACHE_MAX;
        }
    };

    private final ManifestStore manifests;

    public ArtifactService() {
        this(new ManifestStore());
    }

    @Autowired
    public ArtifactService(ManifestStore manifests) {
        this.manifests = manifests;
    }

    /**
     * Resolve an artifact that belongs to a specific run.
     *
     * <p>Only IDs listed in that run's manifest are servable, and only when the
     * resolved path stays inside an allowed evidence directory. This prevents a
     * crafted artifact ID from reading arbitrary files.
     */
    public Optional<Path> resolveForRun(String runId, String artifactId) {
        if (artifactId == null || artifactId.isBlank()) return Optional.empty();
        if (artifactId.contains("..") || artifactId.contains("/") || artifactId.contains("\\")) {
            return Optional.empty();
        }

        if ("manifest".equals(artifactId)) {
            Path canonical = manifests.manifestPath(runId);
            if (Files.isRegularFile(canonical)) return Optional.of(normalize(canonical));
            Path legacy = manifests.legacyManifestPath(runId);
            if (Files.isRegularFile(legacy)) return Optional.of(normalize(legacy));
        }

        Optional<RunManifest> manifest = manifests.read(runId);
        if (manifest.isPresent()) {
            for (RunManifest.ArtifactEntry entry : manifest.get().artifacts()) {
                if (artifactId.equals(entry.artifactId())
                        || artifactId.equals(ManifestStore.stem(entry.location()))) {
                    Optional<Path> resolved = safeResolve(entry.location(), runId);
                    if (resolved.isPresent()) return resolved;
                }
            }
        }
        // Historical runs have no manifest; fall back to the legacy catalog.
        return resolve(artifactId);
    }

    private Optional<Path> safeResolve(String location, String runId) {
        if (location == null || location.isBlank()) return Optional.empty();
        List<Path> roots = new ArrayList<>();
        roots.add(manifests.runDir(runId));
        roots.add(ROOT.resolve("benchmark").resolve("runs"));
        roots.add(ROOT.resolve("benchmark").resolve("results"));
        roots.add(ROOT.resolve("benchmark").resolve("comparisons"));
        roots.add(ROOT.resolve("docs").resolve("experiments").resolve("plans"));

        Path candidate = Path.of(location);
        if (!candidate.isAbsolute()) candidate = ROOT.resolve(candidate);
        candidate = candidate.normalize().toAbsolutePath();
        if (Files.isRegularFile(candidate) && withinAny(candidate, roots)) {
            return Optional.of(candidate);
        }

        // The manifest may have been produced on another machine. Re-anchor the
        // basename inside the run directory before giving up.
        Path fileName = Path.of(location).getFileName();
        if (fileName != null) {
            Path local = manifests.runDir(runId).resolve(fileName).normalize();
            if (Files.isRegularFile(local)) return Optional.of(local);
        }
        return Optional.empty();
    }

    private static boolean withinAny(Path path, List<Path> roots) {
        for (Path root : roots) {
            if (path.startsWith(root.normalize().toAbsolutePath())) return true;
        }
        return false;
    }

    /** Artifact catalog for a run, including the manifest itself. */
    public List<RunArtifact> catalogForRun(String runId, List<RunArtifact> artifacts) {
        List<RunArtifact> catalog = new ArrayList<>();
        Path manifestPath = manifests.manifestPath(runId);
        if (!Files.isRegularFile(manifestPath)) manifestPath = manifests.legacyManifestPath(runId);
        if (Files.isRegularFile(manifestPath)) {
            catalog.add(new RunArtifact("manifest", "application/json",
                    manifestPath.toString(), "Immutable run manifest with checksums"));
        }
        for (RunArtifact artifact : artifacts) {
            boolean duplicate = catalog.stream()
                    .anyMatch(a -> a.artifactId().equals(artifact.artifactId()));
            if (!duplicate) catalog.add(artifact);
        }
        return catalog;
    }

    public Optional<Path> resolve(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) return Optional.empty();
        if (artifactId.contains("..") || artifactId.contains("/") || artifactId.contains("\\")) {
            return Optional.empty(); // reject path traversal
        }
        // Try known results JSON
        Path p = ROOT.resolve("benchmark").resolve("results").resolve(artifactId + ".json");
        if (Files.isRegularFile(p)) return Optional.of(normalize(p));
        // Try known plan text. Artifact IDs are file stems, so the file itself is
        // {artifactId}.txt; the legacy naming convention appended "-explain".
        Path plans = ROOT.resolve("docs").resolve("experiments").resolve("plans");
        for (String name : List.of(artifactId + ".txt", artifactId + "-explain.txt")) {
            p = plans.resolve(name);
            if (Files.isRegularFile(p)) return Optional.of(normalize(p));
        }
        // Fallback: search under docs/experiments/plans/ for exact stem
        try (var stream = Files.list(plans)) {
            return stream.filter(f -> {
                        String name = f.getFileName().toString();
                        return name.equals(artifactId + ".txt")
                                || name.equals(artifactId + "-explain.txt");
                    })
                    .findFirst().map(ArtifactService::normalize);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Path normalize(Path p) {
        try {
            return p.normalize().toAbsolutePath();
        } catch (Exception e) {
            return p;
        }
    }

    public String guessContentType(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (name.endsWith(".csv")) return "text/csv";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    public Optional<Map<String, String>> metadata(String artifactId) {
        return resolve(artifactId).map(p -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("path", p.toString());
            m.put("contentType", guessContentType(p));
            try {
                m.put("sizeBytes", String.valueOf(Files.size(p)));
            } catch (IOException e) {
                m.put("sizeBytes", "unknown");
            }
            return m;
        });
    }

    public String readPlan(String artifactId) throws IOException {
        return PLAN_CACHE.computeIfAbsent(artifactId, key -> {
            try {
                return resolve(key).flatMap(this::readText).orElse("");
            } catch (Exception e) {
                return "";
            }
        });
    }

    public Optional<String> readText(Path path) {
        try (var br = new BufferedReader(new InputStreamReader(Files.newInputStream(path)))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return Optional.of(sb.toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Map<String, BigDecimal> parsePercentiles(JsonNode node) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (node == null || !node.isObject()) return out;
        JsonNode metrics = node.get("metrics");
        if (metrics == null || !metrics.isObject()) return out;

        JsonNode dur = metrics.get("iteration_duration");
        if (dur != null && dur.isObject()) {
            JsonNode v = dur.get("values");
            if (v != null && v.isObject()) {
                put(out, "p50", v.get("p(50)"));
                put(out, "p95", v.get("p(95)"));
                put(out, "p99", v.get("p(99)"));
                put(out, "avg", v.get("avg"));
                put(out, "min", v.get("min"));
                put(out, "max", v.get("max"));
            }
        }
        JsonNode http = metrics.get("http_req_duration");
        if (http != null && http.isObject()) {
            JsonNode v = http.get("values");
            if (v != null && v.isObject()) {
                put(out, "http_p50", v.get("p(50)"));
                put(out, "http_p95", v.get("p(95)"));
                put(out, "http_p99", v.get("p(99)"));
            }
        }
        JsonNode iters = metrics.get("iterations");
        if (iters != null && iters.isObject()) {
            JsonNode v = iters.get("values");
            if (v != null && v.isObject()) {
                put(out, "iterations", v.get("count"));
                put(out, "rps", v.get("rate"));
            }
        }
        return out;
    }

    private static void put(Map<String, BigDecimal> map, String key, JsonNode node) {
        if (node != null && node.isNumber()) {
            map.put(key, node.decimalValue());
        }
    }
}
