package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.measurement.Metrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Phase 1 read-only catalog and run history loader.
 *
 * Loads experiment metadata from a hardcoded registry that mirrors the existing
 * docs/experiments/ and benchmark/results/ artifacts. No benchmark execution.
 */
@Service
public class ExperimentTemplateService {

    private static final Path RESULTS_DIR = Path.of("benchmark", "results");
    private static final Path DOCS_DIR = Path.of("docs", "experiments");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ExperimentTemplateService.class);
    private static final Duration RESULTS_CACHE_TTL = Duration.ofSeconds(2);

    private final Path resultsDir;
    private final Path docsDir;
    private final long cacheTtlNanos;
    private Map<Path, CachedRun> cachedFiles = Map.of();
    private List<ExperimentRun> cachedRuns = List.of();
    private long refreshedAtNanos;
    private boolean hasCachedSnapshot;

    public ExperimentTemplateService() {
        this(RESULTS_DIR, DOCS_DIR, RESULTS_CACHE_TTL);
    }

    ExperimentTemplateService(Path resultsDir, Path docsDir) {
        this(resultsDir, docsDir, RESULTS_CACHE_TTL);
    }

    ExperimentTemplateService(Path resultsDir, Path docsDir, Duration cacheTtl) {
        this.resultsDir = resultsDir;
        this.docsDir = docsDir;
        this.cacheTtlNanos = cacheTtl.toNanos();
    }

    private static final List<ExperimentTemplate> TEMPLATES = List.of(
            new ExperimentTemplate(
                    "QF-000",
                    "Dataset scaling",
                    "How does baseline latency scale with dataset size? Where is the point-lookup floor?",
                    "Small-scale numbers are not evidence; the 1M dataset is the controlled environment.",
                    List.of(
                            new ExperimentTemplate.ParameterField("DATASET", "enum", "rows",
                                    null, null, List.of("10K", "100K", "1M")),
                            new ExperimentTemplate.ParameterField("QUERY", "enum", "type",
                                    null, null, List.of("point", "filtered-search"))
                    ),
                    "~2m",
                    false,
                    List.of("k6 summary JSON", "EXPLAIN plan"),
                    DOCS_DIR.resolve("QF-000-dataset-scaling.md").toString(),
                    List.of(DOCS_DIR.resolve("plans").toString())
            ),
            new ExperimentTemplate(
                    "QF-001",
                    "Filtered search: baseline vs composite index",
                    "Why is filtered search slow at 1M rows, and what does a workload-shaped index buy?",
                    "Baseline uses Parallel Seq Scan over 1M rows; candidate adds a composite B-tree index.",
                    List.of(
                            new ExperimentTemplate.ParameterField("RATE", "integer", "req/s", 1, 200, null),
                            new ExperimentTemplate.ParameterField("DUR", "duration", "seconds", 10, 600, null),
                            new ExperimentTemplate.ParameterField("OUT", "string", "tag", null, null, null)
                    ),
                    "~3m",
                    false,
                    List.of("k6 summary JSON", "EXPLAIN plan"),
                    DOCS_DIR.resolve("QF-001-baseline.md").toString(),
                    List.of(DOCS_DIR.resolve("plans").resolve("qf001-baseline-explain.txt").toString(),
                            DOCS_DIR.resolve("plans").resolve("qf001-indexed-explain.txt").toString())
            ),
            new ExperimentTemplate(
                    "QF-002",
                    "OFFSET vs keyset pagination",
                    "How do OFFSET and KEYSET pagination behave as page depth grows?",
                    "OFFSET cost grows linearly; keyset stays flat because the planner stops at the cursor.",
                    List.of(
                            new ExperimentTemplate.ParameterField("MODE", "enum", "mode",
                                    null, null, List.of("offset", "keyset")),
                            new ExperimentTemplate.ParameterField("RATE", "integer", "req/s", 1, 200, null),
                            new ExperimentTemplate.ParameterField("DUR", "duration", "seconds", 10, 600, null)
                    ),
                    "~3m",
                    false,
                    List.of("k6 summary JSON", "EXPLAIN plan"),
                    DOCS_DIR.resolve("QF-002-pagination.md").toString(),
                    List.of(DOCS_DIR.resolve("plans").resolve("qf002-offset-deep-explain.txt").toString(),
                            DOCS_DIR.resolve("plans").resolve("qf002-keyset-baseline-explain.txt").toString())
            ),
            new ExperimentTemplate(
                    "QF-003",
                    "Concurrency ladder",
                    "What limits the backend under increasing concurrency?",
                    "The 10-connection pool saturates at ~38 req/s regardless of VU count.",
                    List.of(
                            new ExperimentTemplate.ParameterField("VUS", "integer", "virtual users", 10, 200, null),
                            new ExperimentTemplate.ParameterField("DUR", "duration", "seconds", 10, 600, null)
                    ),
                    "~2m",
                    false,
                    List.of("k6 summary JSON"),
                    DOCS_DIR.resolve("QF-003-concurrency.md").toString(),
                    List.of()
            ),
            new ExperimentTemplate(
                    "QF-004",
                    "Index column ordering",
                    "Does an order-first index beat filter-first for keyset, and what does it cost?",
                    "Order-first is faster on keyset reads but slower on filtered search; kept as candidate.",
                    List.of(
                            new ExperimentTemplate.ParameterField("INDEX", "enum", "state",
                                    null, null, List.of("filter-first-only", "both-indexes")),
                            new ExperimentTemplate.ParameterField("MODE", "enum", "mode",
                                    null, null, List.of("keyset", "search")),
                            new ExperimentTemplate.ParameterField("RATE", "integer", "req/s", 1, 200, null)
                    ),
                    "~3m",
                    true,
                    List.of("k6 summary JSON", "EXPLAIN plan"),
                    DOCS_DIR.resolve("QF-004-index-ordering.md").toString(),
                    List.of(DOCS_DIR.resolve("plans").resolve("qf004-keyset-filter-first.txt").toString(),
                            DOCS_DIR.resolve("plans").resolve("qf004-keyset-order-first.txt").toString())
            )
    );

    public List<ExperimentTemplate> findAll() {
        return TEMPLATES;
    }

    public Optional<ExperimentTemplate> findById(String id) {
        return TEMPLATES.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public List<ExperimentRun> findRunsByTemplate(String templateId) {
        return loadRuns().stream()
                .filter(r -> r.templateId().equals(templateId))
                .toList();
    }

    public synchronized List<ExperimentRun> loadRuns() {
        long now = System.nanoTime();
        if (hasCachedSnapshot && now - refreshedAtNanos < cacheTtlNanos) {
            return cachedRuns;
        }
        if (!Files.isDirectory(resultsDir)) {
            cachedFiles = Map.of();
            cachedRuns = List.of();
            refreshedAtNanos = System.nanoTime();
            hasCachedSnapshot = true;
            return List.of();
        }
        try (Stream<Path> stream = Files.list(resultsDir)) {
            Map<Path, CachedRun> refreshed = new LinkedHashMap<>();
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                            CachedRun previous = cachedFiles.get(path);
                            if (previous != null
                                    && previous.size() == attributes.size()
                                    && previous.lastModifiedTime().equals(attributes.lastModifiedTime())) {
                                refreshed.put(path, previous);
                                return;
                            }
                            ExperimentRun run = parseRun(path);
                            refreshed.put(path, new CachedRun(
                                    attributes.size(), attributes.lastModifiedTime(), run));
                        } catch (IOException e) {
                            log.error("Could not read benchmark result metadata for {}", path, e);
                        }
                    });
            cachedFiles = Map.copyOf(refreshed);
            cachedRuns = refreshed.values().stream()
                    .map(CachedRun::run)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(run -> run.startedAt().toEpochMilli()))
                    .toList();
            refreshedAtNanos = System.nanoTime();
            hasCachedSnapshot = true;
            return cachedRuns;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read benchmark results from " + resultsDir, e);
        }
    }

    private ExperimentRun parseRun(Path path) {
        try {
            JsonNode node = MAPPER.readTree(path.toFile());
            String id = path.getFileName().toString().replaceAll("\\.json$", "");
            String templateId = inferTemplateId(id);
            Instant startedAt = inferStartedAt(path);
            Metrics metrics = Metrics.fromK6Node(node);
            List<RunArtifact> artifacts = List.of(
                    new RunArtifact("k6-summary", "application/json", path.toString(), "k6 summary JSON")
            );
            // Attach plans if present
            String planKey = toPlanKey(id);
            Path planPath = docsDir.resolve("plans").resolve(planKey + "-explain.txt");
            if (Files.exists(planPath)) {
                artifacts = new ArrayList<>(artifacts);
                artifacts.add(new RunArtifact("explain-plan", "text/plain", planPath.toString(), "EXPLAIN (ANALYZE, BUFFERS)"));
            }
            return new ExperimentRun(
                    id,
                    templateId,
                    "SUCCEEDED",
                    startedAt,
                    metrics.testRunDurationMs(),
                    Map.of(),
                    Map.of(),
                    metrics,
                    artifacts,
                    "unknown"
            );
        } catch (Exception e) {
            log.error("Could not parse benchmark result {}", path, e);
            return null;
        }
    }

    private static Instant inferStartedAt(Path path) throws IOException {
        return Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis());
    }

    static String inferTemplateId(String filename) {
        if (filename.startsWith("qf000")) return "QF-000";
        if (filename.startsWith("qf001")) return "QF-001";
        if (filename.startsWith("qf002")) return "QF-002";
        if (filename.startsWith("qf003")) return "QF-003";
        if (filename.startsWith("qf004")) return "QF-004";
        return "UNKNOWN";
    }

    static Instant inferStartedAt(String filename, com.fasterxml.jackson.databind.JsonNode node) {
        Path path = Path.of("benchmark", "results", filename + ".json");
        try {
            return inferStartedAt(path);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static String toPlanKey(String runId) {
        // map known run IDs to plan file prefixes
        return switch (runId) {
            case "qf001-baseline-search-1M-r10" -> "qf001-baseline";
            case "qf001-indexed-search-1M-r10", "qf001-indexed-search-1M-r30" -> "qf001-indexed";
            case "qf002-offset-1M" -> "qf002-offset-deep";
            case "qf002-keyset-1M" -> "qf002-keyset-baseline";
            case "qf004-keyset-both-indexes" -> "qf004-keyset-both-indexes";
            case "qf004-keyset-filter-first-only" -> "qf004-keyset-filter-first";
            case "qf004-search-both-indexes-r10" -> "qf004-search-both-indexes";
            case "qf004-search-filter-first-only-r10" -> "qf004-search-filter-first-only";
            default -> runId;
        };
    }

    private record CachedRun(long size, java.nio.file.attribute.FileTime lastModifiedTime, ExperimentRun run) {}
}
