package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.artifact.RunManifest;
import com.bakr.queryforge.measurement.Metrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds comparison reports with explicit compatibility rules and metric deltas.
 *
 * <p>Compatibility is judged from stored manifest fingerprints, never inferred
 * from template names. A comparison is blocked when the query definition,
 * PostgreSQL major version, dataset size, or index state differ; weaker
 * differences (patch version, commit, host resources, rate) are warnings.
 */
@Service
public class ComparisonService {

    private static final Path COMPARISONS_DIR = Path.of("benchmark", "comparisons");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final List<String> QUERY_KEYS = List.of("DATASET", "QUERY", "MODE", "INDEX");
    private static final List<String> WORKLOAD_KEYS = List.of("RATE", "DUR", "VUS");

    private final ExperimentRunService runs;
    private final Path comparisonsDir;

    @Autowired
    public ComparisonService(ExperimentRunService runs) {
        this(runs, COMPARISONS_DIR);
    }

    public ComparisonService(ExperimentRunService runs, Path comparisonsDir) {
        this.runs = runs;
        this.comparisonsDir = comparisonsDir;
    }

    public ComparisonReport compare(String leftId, String rightId, String createdBy) throws IOException {
        ExperimentRun left = runs.findById(leftId)
                .orElseThrow(() -> new IllegalArgumentException("left run not found: " + leftId));
        ExperimentRun right = runs.findById(rightId)
                .orElseThrow(() -> new IllegalArgumentException("right run not found: " + rightId));

        RunManifest leftManifest = runs.manifest(leftId).orElse(null);
        RunManifest rightManifest = runs.manifest(rightId).orElse(null);
        Map<String, Object> leftFp = leftManifest == null ? Map.of() : leftManifest.environmentFingerprint();
        Map<String, Object> rightFp = rightManifest == null ? Map.of() : rightManifest.environmentFingerprint();

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> diff = new LinkedHashMap<>();

        // 1. Query definition must match.
        if (!Objects.equals(left.templateId(), right.templateId())) {
            blockers.add("Different experiment templates: " + left.templateId() + " vs " + right.templateId());
            diff.put("template", left.templateId() + " → " + right.templateId());
        }
        for (String key : QUERY_KEYS) {
            String l = left.parameters().get(key);
            String r = right.parameters().get(key);
            if (l != null && r != null && !l.equals(r)) {
                blockers.add("Different " + key + ": " + l + " vs " + r);
                diff.put(key.toLowerCase(), l + " → " + r);
            }
        }

        // 2. Both runs must have completed successfully.
        if (!"SUCCEEDED".equals(left.state()) || !"SUCCEEDED".equals(right.state())) {
            blockers.add("Both runs must be SUCCEEDED (left=" + left.state() + ", right=" + right.state() + ")");
            diff.put("leftState", left.state());
            diff.put("rightState", right.state());
        }

        // 3. Environment fingerprint.
        String leftProduct = text(leftFp.get("databaseProduct"));
        String rightProduct = text(rightFp.get("databaseProduct"));
        int leftMajor = numberInt(leftFp.get("postgresMajorVersion"));
        int rightMajor = numberInt(rightFp.get("postgresMajorVersion"));
        String leftPg = text(leftFp.get("postgresVersion"));
        String rightPg = text(rightFp.get("postgresVersion"));
        long leftRows = numberLong(leftFp.get("rowCount"));
        long rightRows = numberLong(rightFp.get("rowCount"));
        String leftIndexState = !"unknown".equals(leftManifest == null ? "unknown" : leftManifest.indexState())
                ? leftManifest.indexState() : text(leftFp.get("indexState"));
        String rightIndexState = !"unknown".equals(rightManifest == null ? "unknown" : rightManifest.indexState())
                ? rightManifest.indexState() : text(rightFp.get("indexState"));

        if (leftMajor > 0 && rightMajor > 0 && leftMajor != rightMajor) {
            blockers.add("Different PostgreSQL major version: " + leftMajor + " vs " + rightMajor);
            diff.put("postgresMajorVersion", leftMajor + " → " + rightMajor);
        } else if (leftPg != null && rightPg != null && !leftPg.equals(rightPg)) {
            warnings.add("PostgreSQL patch version differs: " + leftPg + " vs " + rightPg);
            diff.put("postgresVersion", leftPg + " → " + rightPg);
        }
        if (leftProduct != null && rightProduct != null && !leftProduct.equals(rightProduct)) {
            blockers.add("Different database engines: " + leftProduct + " vs " + rightProduct);
            diff.put("databaseProduct", leftProduct + " → " + rightProduct);
        }
        if (leftRows >= 0 && rightRows >= 0 && leftRows != rightRows) {
            blockers.add("Different dataset row counts: " + leftRows + " vs " + rightRows);
            diff.put("rowCount", leftRows + " → " + rightRows);
        }
        if (leftIndexState != null && rightIndexState != null
                && !"unknown".equalsIgnoreCase(leftIndexState)
                && !"unknown".equalsIgnoreCase(rightIndexState)
                && !leftIndexState.equalsIgnoreCase(rightIndexState)) {
            blockers.add("Different index state: " + leftIndexState + " vs " + rightIndexState);
            diff.put("indexState", leftIndexState + " → " + rightIndexState);
        }
        if (leftManifest == null || rightManifest == null) {
            warnings.add("One or both runs have no manifest; environment compatibility could not be fully verified.");
        } else if (Boolean.FALSE.equals(leftFp.get("available")) || Boolean.FALSE.equals(rightFp.get("available"))) {
            warnings.add("Environment fingerprint was unavailable when the run was recorded.");
        }

        String leftCommit = text(leftFp.get("appCommit"));
        String rightCommit = text(rightFp.get("appCommit"));
        if (leftCommit != null && rightCommit != null && !leftCommit.equals(rightCommit)) {
            warnings.add("Application commit differs: " + leftCommit + " vs " + rightCommit);
            diff.put("appCommit", leftCommit + " → " + rightCommit);
        }
        addResourceWarning(warnings, diff, "cpuCores", leftFp.get("cpuCores"), rightFp.get("cpuCores"));
        addResourceWarning(warnings, diff, "memoryMb", leftFp.get("memoryMb"), rightFp.get("memoryMb"));

        // 4. Same query, different load: comparable but no longer like-for-like.
        for (String key : WORKLOAD_KEYS) {
            String l = left.parameters().get(key);
            String r = right.parameters().get(key);
            if (l != null && r != null && !l.equals(r)) {
                warnings.add("Workload parameter " + key + " differs: " + l + " vs " + r);
                diff.put(key.toLowerCase(), l + " → " + r);
            }
        }

        boolean compatible = blockers.isEmpty();
        CompatibilityVerdict verdict = new CompatibilityVerdict(
                leftId, rightId, compatible, List.copyOf(blockers), List.copyOf(warnings), diff);
        Map<String, MetricDelta> deltas = metricsDelta(left.metrics(), right.metrics());

        String markdown = ComparisonReportRenderer.buildMarkdown(left, right, verdict, deltas);
        String html = ComparisonReportRenderer.buildHtml(left, right, verdict, deltas);
        String reportId = "cmp-" + leftId + "-" + rightId + "-" + System.currentTimeMillis();

        List<RunArtifact> artifacts = new ArrayList<>();
        try {
            Files.createDirectories(comparisonsDir);
            String mdFile = reportId + ".md";
            String htmlFile = reportId + ".html";
            Files.writeString(comparisonsDir.resolve(mdFile), markdown, StandardCharsets.UTF_8);
            Files.writeString(comparisonsDir.resolve(htmlFile), html, StandardCharsets.UTF_8);
            artifacts.add(new RunArtifact("comparison-markdown", "text/markdown",
                    comparisonsDir.resolve(mdFile).toString(), "Markdown comparison report"));
            artifacts.add(new RunArtifact("comparison-html", "text/html",
                    comparisonsDir.resolve(htmlFile).toString(), "HTML comparison report"));
        } catch (IOException e) {
            // artifacts are optional; the report body is still returned
        }

        ComparisonReport report = new ComparisonReport(
                reportId, leftId, rightId, verdict, deltas,
                "markdown", markdown, artifacts, Instant.now(), createdBy
        );

        try {
            Files.createDirectories(comparisonsDir);
            Path meta = comparisonsDir.resolve(reportId + ".json");
            Files.writeString(meta, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            // best effort; generated reports remain downloadable from memory
        }

        return report;
    }

    public Optional<ComparisonReport> findById(String id) {
        Path meta = comparisonsDir.resolve(id + ".json");
        if (!Files.isRegularFile(meta)) return Optional.empty();
        try {
            var node = MAPPER.readTree(meta.toFile());
            String leftId = node.path("leftRunId").asText();
            String rightId = node.path("rightRunId").asText();
            boolean compatible = node.path("verdict").path("compatible").asBoolean(false);
            List<String> blockers = new ArrayList<>();
            node.path("verdict").path("blockers").forEach(b -> blockers.add(b.asText()));
            List<String> warnings = new ArrayList<>();
            node.path("verdict").path("warnings").forEach(w -> warnings.add(w.asText()));
            Map<String, String> diff = new LinkedHashMap<>();
            node.path("verdict").path("diff").fields().forEachRemaining(e -> diff.put(e.getKey(), e.getValue().asText()));
            Map<String, MetricDelta> deltas = node.path("metricsDelta").isObject()
                    ? MAPPER.convertValue(node.path("metricsDelta"),
                    new TypeReference<LinkedHashMap<String, MetricDelta>>() {})
                    : Map.of();
            String format = node.path("format").asText("markdown");
            String content = node.path("content").asText("");
            List<RunArtifact> artifacts = new ArrayList<>();
            node.path("artifacts").forEach(a -> artifacts.add(new RunArtifact(
                    a.path("kind").asText(""),
                    a.path("contentType").asText(""),
                    a.path("location").asText(""),
                    a.path("description").asText("")
            )));
            Instant createdAt = ManifestStore.parseInstant(node.get("createdAt"));
            if (createdAt == null) createdAt = Instant.now();
            String createdBy = node.path("createdBy").asText("");
            return Optional.of(new ComparisonReport(id, leftId, rightId,
                    new CompatibilityVerdict(leftId, rightId, compatible, blockers, warnings, diff),
                    deltas, format, content, artifacts, createdAt, createdBy));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<ComparisonReport> loadAll() {
        if (!Files.isDirectory(comparisonsDir)) return List.of();
        try (var stream = Files.list(comparisonsDir)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> findById(p.getFileName().toString().replaceAll("\\.json$", "")))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparing(ComparisonReport::createdAt))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void addResourceWarning(List<String> warnings, Map<String, String> diff,
                                           String key, Object left, Object right) {
        long l = numberLong(left);
        long r = numberLong(right);
        if (l > 0 && r > 0 && l != r) {
            warnings.add("Host " + key + " differs: " + l + " vs " + r);
            diff.put(key, l + " → " + r);
        }
    }

    private static Map<String, MetricDelta> metricsDelta(Metrics left, Metrics right) {
        Map<String, MetricDelta> out = new LinkedHashMap<>();
        if (left == null || right == null) return out;
        for (String key : List.of("p50", "p95", "p99", "avg")) {
            addDelta(out, "iteration." + key, left.iterationPercentiles().get(key),
                    right.iterationPercentiles().get(key), "ms");
            addDelta(out, "http." + key, left.httpPercentiles().get(key),
                    right.httpPercentiles().get(key), "ms");
        }
        addDelta(out, "rps", BigDecimal.valueOf(left.rps()), BigDecimal.valueOf(right.rps()), "req/s");
        addDelta(out, "iterations", BigDecimal.valueOf(left.iterationCount()),
                BigDecimal.valueOf(right.iterationCount()), "count");
        addDelta(out, "testRunDurationMs", BigDecimal.valueOf(left.testRunDurationMs()),
                BigDecimal.valueOf(right.testRunDurationMs()), "ms");
        return out;
    }

    private static void addDelta(Map<String, MetricDelta> out, String key,
                                 BigDecimal left, BigDecimal right, String unit) {
        if (left == null || right == null) return;
        BigDecimal delta = right.subtract(left);
        BigDecimal pct = left.compareTo(BigDecimal.ZERO) == 0
                ? null
                : delta.multiply(BigDecimal.valueOf(100))
                .divide(left, 2, RoundingMode.HALF_UP);
        out.put(key, new MetricDelta(left, right, delta, pct, unit));
    }

    public String buildHtml(ExperimentRun left, ExperimentRun right,
                            CompatibilityVerdict verdict, Map<String, MetricDelta> deltas) {
        return ComparisonReportRenderer.buildHtml(left, right, verdict, deltas);
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "unknown".equalsIgnoreCase(text) ? null : text;
    }

    private static int numberInt(Object value) {
        long n = numberLong(value);
        return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
    }

    private static long numberLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

}
