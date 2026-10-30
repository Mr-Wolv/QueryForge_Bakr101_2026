package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public Optional<Path> resolve(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) return Optional.empty();
        if (artifactId.contains("..") || artifactId.contains("/") || artifactId.contains("\\")) {
            return Optional.empty(); // reject path traversal
        }
        // Try known results JSON
        Path p = ROOT.resolve("benchmark").resolve("results").resolve(artifactId + ".json");
        if (Files.isRegularFile(p)) return Optional.of(normalize(p));
        // Try known plan text
        p = ROOT.resolve("docs").resolve("experiments").resolve("plans").resolve(artifactId + "-explain.txt");
        if (Files.isRegularFile(p)) return Optional.of(normalize(p));
        // Fallback: search under docs/experiments/plans/ for exact stem
        try (var stream = Files.list(ROOT.resolve("docs").resolve("experiments").resolve("plans"))) {
            return stream.filter(f -> f.getFileName().toString().equals(artifactId + "-explain.txt"))
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
