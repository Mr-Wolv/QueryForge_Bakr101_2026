package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.PlanParser;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.measurement.Metrics;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
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

    public List<ExperimentRun> loadRuns() {
        if (!Files.isDirectory(RESULTS_DIR)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(RESULTS_DIR)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .map(this::parseRun)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(r -> r.startedAt().toEpochMilli()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private ExperimentRun parseRun(Path path) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(path.toFile());
            String id = path.getFileName().toString().replaceAll("\\.json$", "");
            String templateId = inferTemplateId(id);
            Instant startedAt = inferStartedAt(id, node);
            Metrics metrics = Metrics.fromK6Node(node);
            List<RunArtifact> artifacts = List.of(
                    new RunArtifact("k6-summary", "application/json", path.toString(), "k6 summary JSON")
            );
            // Attach plans if present
            String planKey = toPlanKey(id);
            Path planPath = DOCS_DIR.resolve("plans").resolve(planKey + "-explain.txt");
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
                    metrics,
                    artifacts
            );
        } catch (Exception e) {
            return null;
        }
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
            return Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis());
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
}
