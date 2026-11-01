package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads and writes immutable run manifests.
 *
 * <p>Canonical layout is {@code benchmark/runs/{runId}/manifest.json}. The older
 * flat layout {@code benchmark/runs/{runId}.json} is still read so historical
 * evidence remains visible after the migration.
 *
 * <p>The store is a Spring component with an injectable root directory so tests
 * can write manifests into a temporary directory instead of the repository.
 */
@Component
public class ManifestStore {

    public static final Path RUNS_DIR = Path.of("benchmark", "runs");
    public static final String MANIFEST_FILE = "manifest.json";

    private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,160}");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path runsDir;

    public ManifestStore() {
        this(RUNS_DIR);
    }

    public ManifestStore(Path runsDir) {
        this.runsDir = Objects.requireNonNull(runsDir, "runsDir").toAbsolutePath().normalize();
    }

    public Path runDir(String runId) {
        requireSafeRunId(runId);
        return runsDir.resolve(runId).normalize();
    }

    public Path manifestPath(String runId) {
        return runDir(runId).resolve(MANIFEST_FILE);
    }

    public Path legacyManifestPath(String runId) {
        requireSafeRunId(runId);
        return runsDir.resolve(runId + ".json").normalize();
    }

    /** Write the manifest atomically and return the stamped copy that was persisted. */
    public RunManifest write(RunManifest manifest) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Path dir = runDir(manifest.runId());
        Files.createDirectories(dir);

        RunManifest unstamped = new RunManifest(
                manifest.runId(), manifest.templateId(), manifest.templateVersion(),
                manifest.state(), manifest.startedAt(), manifest.endedAt(),
                manifest.durationMs(), manifest.outcome(), manifest.parameters(),
                manifest.labels(), manifest.environmentFingerprint(), manifest.migrationVersion(),
                manifest.indexState(), manifest.resourceLimits(), manifest.appCommit(),
                manifest.summaryMetrics(), manifest.artifacts(), null);
        String checksum = sha256(canonicalBytes(unstamped));

        RunManifest stamped = new RunManifest(
                unstamped.runId(), unstamped.templateId(), unstamped.templateVersion(),
                unstamped.state(), unstamped.startedAt(), unstamped.endedAt(),
                unstamped.durationMs(), unstamped.outcome(), unstamped.parameters(),
                unstamped.labels(), unstamped.environmentFingerprint(), unstamped.migrationVersion(),
                unstamped.indexState(), unstamped.resourceLimits(), unstamped.appCommit(),
                unstamped.summaryMetrics(), unstamped.artifacts(), checksum);

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(stamped);
        Path target = manifestPath(manifest.runId());
        Path tmp = dir.resolve("." + MANIFEST_FILE + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return stamped;
    }

    public Optional<RunManifest> read(String runId) {
        requireSafeRunId(runId);
        Optional<RunManifest> canonical = readPath(manifestPath(runId));
        return canonical.isPresent() ? canonical : readPath(legacyManifestPath(runId));
    }

    public List<RunManifest> readAll() {
        return readAll(0, Integer.MAX_VALUE);
    }

    /**
     * Read every stored manifest for the one-time database import. Unlike the
     * best-effort catalog reader, corrupt manifests fail startup rather than
     * silently disappearing from durable run history.
     */
    public List<RunManifest> readAllStrict() throws IOException {
        if (!Files.isDirectory(runsDir)) return List.of();

        List<Path> paths;
        try (var stream = Files.list(runsDir)) {
            paths = stream.sorted().toList();
        }

        Map<String, RunManifest> byId = new LinkedHashMap<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                Path manifest = path.resolve(MANIFEST_FILE);
                if (Files.isRegularFile(manifest)) {
                    readRequiredManifest(manifest).ifPresent(m -> byId.put(m.runId(), m));
                }
            } else if (path.getFileName().toString().endsWith(".json")) {
                RunManifest manifest = readRequiredManifest(path)
                        .orElseThrow(() -> new IOException("run manifest is empty: " + path));
                byId.putIfAbsent(manifest.runId(), manifest);
            }
        }
        return List.copyOf(byId.values());
    }

    private Optional<RunManifest> readRequiredManifest(Path path) throws IOException {
        Optional<RunManifest> parsed = readPath(path);
        if (parsed.isEmpty()) {
            throw new IOException("could not parse run manifest: " + path);
        }
        return parsed;
    }

    public long countAll() {
        if (!Files.isDirectory(runsDir)) return 0;
        long count = 0;
        try (var stream = Files.list(runsDir)) {
            count = stream
                    .filter(Files::isDirectory)
                    .count();
        } catch (IOException e) {
            return 0;
        }
        try (var stream = Files.list(runsDir)) {
            count += stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .count();
        } catch (IOException e) {
            return count;
        }
        return count;
    }

    /** Returns a page of manifests sorted by startedAt (newest first). */
    public List<RunManifest> readAll(int page, int size) {
        if (!Files.isDirectory(runsDir)) return List.of();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);

        Map<String, RunManifest> byId = new LinkedHashMap<>();
        List<Path> legacy = new ArrayList<>();
        try (var stream = Files.list(runsDir)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path)) {
                    readPath(path.resolve(MANIFEST_FILE))
                            .ifPresent(m -> byId.put(m.runId(), m));
                } else if (path.getFileName().toString().endsWith(".json")) {
                    legacy.add(path);
                }
            });
        } catch (IOException e) {
            return List.of();
        }
        for (Path path : legacy) {
            readPath(path).ifPresent(m -> byId.putIfAbsent(m.runId(), m));
        }

        List<RunManifest> all = byId.values().stream()
                .sorted(Comparator.comparing(RunManifest::startedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        int from = safePage * safeSize;
        if (from >= all.size()) return List.of();
        return all.subList(from, Math.min(all.size(), from + safeSize));
    }

    /** Recompute the checksum over the canonical manifest form (excluding the checksum). */
    public String checksum(RunManifest manifest) {
        RunManifest unstamped = new RunManifest(
                manifest.runId(), manifest.templateId(), manifest.templateVersion(),
                manifest.state(), manifest.startedAt(), manifest.endedAt(),
                manifest.durationMs(), manifest.outcome(), manifest.parameters(),
                manifest.labels(), manifest.environmentFingerprint(), manifest.migrationVersion(),
                manifest.indexState(), manifest.resourceLimits(), manifest.appCommit(),
                manifest.summaryMetrics(), manifest.artifacts(), null);
        return sha256(canonicalBytes(unstamped));
    }

    private static byte[] canonicalBytes(RunManifest manifest) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot serialize manifest", e);
        }
    }

    private Optional<RunManifest> readPath(Path path) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            JsonNode node = MAPPER.readTree(path.toFile());
            String runId = node.path("runId").asText("").trim();
            if (runId.isEmpty()) {
                if (path.getParent() != null && !"runs".equals(path.getParent().getFileName().toString())) {
                    runId = path.getParent().getFileName().toString();
                } else {
                    runId = path.getFileName().toString().replaceAll("\\.json$", "");
                }
            }

            Map<String, String> parameters = stringMap(node.path("parameters"));
            Map<String, String> labels = stringMap(node.path("labels"));
            Map<String, Object> environment = objectMap(node.path("environmentFingerprint"));
            Map<String, Object> limits = objectMap(node.path("resourceLimits"));
            Map<String, Object> summary = objectMap(node.path("summaryMetrics"));

            List<RunManifest.ArtifactEntry> artifacts = new ArrayList<>();
            for (JsonNode artifact : node.path("artifacts")) {
                artifacts.add(new RunManifest.ArtifactEntry(
                        artifact.path("artifactId").asText(stem(artifact.path("location").asText(""))),
                        artifact.path("kind").asText("artifact"),
                        artifact.path("contentType").asText("application/octet-stream"),
                        artifact.path("location").asText(""),
                        artifact.path("description").asText(""),
                        artifact.path("checksumSha256").asText(null)));
            }

            return Optional.of(new RunManifest(
                    runId,
                    node.path("templateId").asText("UNKNOWN"),
                    node.path("templateVersion").asText("1.0"),
                    node.path("state").asText("UNKNOWN"),
                    parseInstant(node.get("startedAt")),
                    parseInstant(node.get("endedAt")),
                    node.path("durationMs").asLong(0),
                    node.path("outcome").asText(""),
                    parameters,
                    labels,
                    environment,
                    node.path("migrationVersion").asText("V1"),
                    node.path("indexState").asText("unknown"),
                    limits,
                    node.path("appCommit").asText("unknown"),
                    summary,
                    artifacts,
                    node.path("checksumSha256").asText(null)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Instant parseInstant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) {
            double value = node.asDouble();
            long millis = value < 1_000_000_000_000d
                    ? Math.round(value * 1000d)
                    : Math.round(value);
            return Instant.ofEpochMilli(millis);
        }
        String text = node.asText("").trim();
        if (text.isEmpty()) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                double value = Double.parseDouble(text);
                long millis = value < 1_000_000_000_000d
                        ? Math.round(value * 1000d)
                        : Math.round(value);
                return Instant.ofEpochMilli(millis);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static String stem(String location) {
        if (location == null || location.isBlank()) return "artifact";
        String name = Path.of(location).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        return MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, String>>() {});
    }

    private static Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        return MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static void requireSafeRunId(String runId) {
        if (runId == null || !SAFE_RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("invalid run id");
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
