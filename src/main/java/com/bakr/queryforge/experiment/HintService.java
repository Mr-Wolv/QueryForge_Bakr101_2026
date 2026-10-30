package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.PlanParser;
import com.bakr.queryforge.measurement.Metrics;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates deterministic, data-grounded optimization hints from plan text and metrics.
 */
@Service
public class HintService {

    private final ArtifactService artifactService;
    private static final Pattern BUFFER_HIT_PATTERN = Pattern.compile("Shared hit=(\\d+)");
    private static final Pattern BUFFER_READ_PATTERN = Pattern.compile("Shared read=(\\d+)");

    public HintService(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    public List<Hint> generate(String planText, Metrics metrics) {
        List<Hint> hints = new ArrayList<>();
        if (planText == null || planText.isBlank()) return hints;

        PlanParser parser = new PlanParser();
        PlanParser.PlanNode root = parser.parse(planText);
        Set<String> nodeTypes = collectNodeTypes(root);

        // Rule 1: Seq Scan on non-trivial table → index suggestion
        if (nodeTypes.contains("Seq Scan")) {
            hints.add(new Hint(
                    "h1",
                    "suggestion",
                    "Seq Scan detected",
                    "The planner chose a sequential scan. A btree index on the filtered column(s) could reduce p95 latency for medium-to-large tables."
            ));
        }

        // Rule 2: Hash Join on small inner → nested loop might be faster
        if (nodeTypes.contains("Hash Join")) {
            hints.add(new Hint(
                    "h2",
                    "info",
                    "Hash Join in use",
                    "Hash Join is optimal for large unsorted inputs. If the inner relation is small, a Nested Loop with index lookup may reduce overhead."
            ));
        }

        // Rule 3: Sort with high cost → keyset pagination or pre-sorted index
        if (nodeTypes.contains("Sort")) {
            hints.add(new Hint(
                    "h3",
                    "suggestion",
                    "Sort node detected",
                    "An explicit Sort may indicate keyset pagination or ORDER BY without a matching index. Consider an index on the sort key."
            ));
        }

        // Rule 4: High buffer read ratio
        long totalHit = 0, totalRead = 0;
        Matcher hitM = BUFFER_HIT_PATTERN.matcher(planText);
        Matcher readM = BUFFER_READ_PATTERN.matcher(planText);
        while (hitM.find()) totalHit += Long.parseLong(hitM.group(1));
        while (readM.find()) totalRead += Long.parseLong(readM.group(1));
        if (totalRead > 0 && totalHit == 0) {
            hints.add(new Hint(
                    "h4",
                    "warning",
                    "Zero buffer cache hits",
                    "All reads hit disk. Increase shared_buffers, warm the cache, or reduce the working set."
            ));
        }

        // Rule 5: p99 tail vs p95
        Map<String, java.math.BigDecimal> pctls = new LinkedHashMap<>(metrics.iterationPercentiles);
        pctls.putAll(metrics.httpPercentiles);
        java.math.BigDecimal p95 = pctls.getOrDefault("p95", java.math.BigDecimal.ZERO);
        java.math.BigDecimal p99 = pctls.getOrDefault("p99", java.math.BigDecimal.ZERO);
        if (p95.compareTo(java.math.BigDecimal.ZERO) > 0 && p99.compareTo(p95.multiply(java.math.BigDecimal.valueOf(2))) > 0) {
            hints.add(new Hint(
                    "h5",
                    "warning",
                    "High p99 tail latency",
                    "p99 is more than 2× p95. Investigate lock contention, cache eviction, or outlier parameter values."
            ));
        }

        return hints;
    }

    private Set<String> collectNodeTypes(PlanParser.PlanNode node) {
        Set<String> types = new LinkedHashSet<>();
        if (node != null) {
            types.add(node.type());
            for (PlanParser.PlanNode child : node.children()) {
                types.addAll(collectNodeTypes(child));
            }
        }
        return types;
    }

    public record Hint(String id, String severity, String title, String body) {}
}
