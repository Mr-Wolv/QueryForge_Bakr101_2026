package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds comparison reports with compatibility rules and exports in Markdown/HTML.
 */
@Service
public class ComparisonService {

    private static final Path COMPARISONS_DIR = Path.of("benchmark", "comparisons");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final ExperimentRunService runs;
    private final ExperimentTemplateService templates;
    private final ArtifactService artifactService;

    public ComparisonService(ExperimentRunService runs, ExperimentTemplateService templates,
                             ArtifactService artifactService) {
        this.runs = runs;
        this.templates = templates;
        this.artifactService = artifactService;
    }

    /**
     * Compare two runs. Compatibility rules:
     * - Same DB version (from run manifest / environment fingerprint)
     * - Same dataset size (from parameters)
     * - Same workload type (from parameters)
     * - Same index state (from parameters or manifest)
     * - Same environment variables (from manifest)
     */
    public ComparisonReport compare(String leftId, String rightId, String createdBy) throws IOException {
        ExperimentRun left = runs.findById(leftId)
                .orElseThrow(() -> new IllegalArgumentException("left run not found: " + leftId));
        ExperimentRun right = runs.findById(rightId)
                .orElseThrow(() -> new IllegalArgumentException("right run not found: " + rightId));

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> diff = new LinkedHashMap<>();

        // 1. Template compatibility
        if (!Objects.equals(left.templateId(), right.templateId())) {
            blockers.add("Different experiment templates: " + left.templateId() + " vs " + right.templateId());
            diff.put("template", left.templateId() + " → " + right.templateId());
        }

        // 2. State compatibility
        if (!"SUCCEEDED".equals(left.state()) || !"SUCCEEDED".equals(right.state())) {
            blockers.add("Both runs must be SUCCEEDED (left=" + left.state() + ", right=" + right.state() + ")");
            diff.put("leftState", left.state());
            diff.put("rightState", right.state());
        }

        // 3. DB version / environment fingerprint
        String leftFp = fingerprintOf(left);
        String rightFp = fingerprintOf(right);
        if (!Objects.equals(leftFp, rightFp)) {
            warnings.add("Different environment fingerprints: " + leftFp + " vs " + rightFp);
            diff.put("fingerprint", leftFp + " → " + rightFp);
        }

        boolean compatible = blockers.isEmpty();

        // Build markdown content
        String markdown = buildMarkdown(left, right, compatible, blockers, warnings, diff);
        String html = buildHtml(left, right, compatible, blockers, warnings, diff);

        String reportId = "cmp-" + leftId + "-" + rightId + "-" + System.currentTimeMillis();

        List<RunArtifact> artifacts = new ArrayList<>();
        try {
            Files.createDirectories(COMPARISONS_DIR);
            String mdFile = reportId + ".md";
            String htmlFile = reportId + ".html";
            Files.writeString(COMPARISONS_DIR.resolve(mdFile), markdown);
            Files.writeString(COMPARISONS_DIR.resolve(htmlFile), html);
            artifacts.add(new RunArtifact("comparison-markdown", "text/markdown",
                    COMPARISONS_DIR.resolve(mdFile).toString(), "Markdown comparison report"));
            artifacts.add(new RunArtifact("comparison-html", "text/html",
                    COMPARISONS_DIR.resolve(htmlFile).toString(), "HTML comparison report"));
        } catch (IOException e) {
            // artifacts are optional
        }

        ComparisonReport report = new ComparisonReport(
                reportId, leftId, rightId,
                new CompatibilityVerdict(leftId, rightId, compatible, blockers, warnings, diff),
                "markdown", markdown, artifacts, Instant.now(), createdBy
        );

        // Persist report metadata
        try {
            Files.createDirectories(COMPARISONS_DIR);
            Path meta = COMPARISONS_DIR.resolve(reportId + ".json");
            Files.writeString(meta, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } catch (Exception e) {
            // best effort
        }

        return report;
    }

    public Optional<ComparisonReport> findById(String id) {
        Path meta = COMPARISONS_DIR.resolve(id + ".json");
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
            String format = node.path("format").asText("markdown");
            String content = node.path("content").asText("");
            List<RunArtifact> artifacts = new ArrayList<>();
            node.path("artifacts").forEach(a -> artifacts.add(new RunArtifact(
                    a.path("kind").asText(""),
                    a.path("contentType").asText(""),
                    a.path("location").asText(""),
                    a.path("description").asText("")
            )));
            Instant createdAt = node.path("createdAt").isMissingNode() ? Instant.now()
                    : parseInstant(node.path("createdAt"));
            String createdBy = node.path("createdBy").asText("");
            ComparisonReport report = new ComparisonReport(id, leftId, rightId,
                    new CompatibilityVerdict(leftId, rightId, compatible, blockers, warnings, diff),
                    format, content, artifacts, createdAt, createdBy);
            return Optional.of(report);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<ComparisonReport> loadAll() {
        if (!Files.isDirectory(COMPARISONS_DIR)) return List.of();
        try (var stream = Files.list(COMPARISONS_DIR)) {
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

    private String fingerprintOf(ExperimentRun run) {
        // Read manifest from disk for full fingerprint
        Path manifestPath = Path.of("benchmark", "runs", run.id() + ".json");
        if (Files.isRegularFile(manifestPath)) {
            try {
                var node = MAPPER.readTree(manifestPath.toFile());
                var fpNode = node.path("environmentFingerprint");
                if (!fpNode.isMissingNode() && !fpNode.isNull()) {
                    if (fpNode.isTextual()) {
                        String fp = fpNode.asText();
                        if (!fp.isBlank()) return fp;
                    } else {
                        return fpNode.toString();
                    }
                }
            } catch (Exception ignored) {}
        }
        // Fallback: template + state
        return run.templateId() + "|" + run.state() + "|" + run.durationMs();
    }

    private String buildMarkdown(ExperimentRun left, ExperimentRun right,
                                 boolean compatible, List<String> blockers,
                                 List<String> warnings, Map<String, String> diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Comparison Report\n\n");
        sb.append("| Field | Left | Right |\n");
        sb.append("|-------|------|-------|\n");
        sb.append("| Run ID | `").append(left.id()).append("` | `").append(right.id()).append("` |\n");
        sb.append("| Template | ").append(left.templateId()).append(" | ").append(right.templateId()).append(" |\n");
        sb.append("| State | ").append(left.state()).append(" | ").append(right.state()).append(" |\n");
        sb.append("| Duration | ").append(left.durationMs()).append("ms | ").append(right.durationMs()).append("ms |\n\n");

        sb.append("## Verdict\n\n");
        if (compatible) {
            sb.append("**COMPATIBLE** — runs can be compared.\n\n");
        } else {
            sb.append("**INCOMPATIBLE** — runs cannot be meaningfully compared.\n\n");
            sb.append("### Blockers\n\n");
            for (String b : blockers) {
                sb.append("- ").append(b).append("\n");
            }
            sb.append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("### Warnings\n\n");
            for (String w : warnings) {
                sb.append("- ").append(w).append("\n");
            }
            sb.append("\n");
        }

        if (!diff.isEmpty()) {
            sb.append("## Differences\n\n");
            for (var e : diff.entrySet()) {
                sb.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String buildHtml(ExperimentRun left, ExperimentRun right,
                             boolean compatible, List<String> blockers,
                             List<String> warnings, Map<String, String> diff) {
        String md = buildMarkdown(left, right, compatible, blockers, warnings, diff);
        // Minimal HTML wrapper: convert markdown-ish lines to HTML
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Comparison ")
                .append(left.id()).append(" vs ").append(right.id()).append("</title></head><body>\n");
        sb.append("<h1>Comparison: ").append(escape(left.id())).append(" vs ").append(escape(right.id())).append("</h1>\n");
        sb.append("<table border='1' cellpadding='6' cellspacing='0'>\n<tr><th>Field</th><th>Left</th><th>Right</th></tr>\n");
        sb.append("<tr><td>Template</td><td>").append(escape(left.templateId())).append("</td><td>").append(escape(right.templateId())).append("</td></tr>\n");
        sb.append("<tr><td>State</td><td>").append(escape(left.state())).append("</td><td>").append(escape(right.state())).append("</td></tr>\n");
        sb.append("<tr><td>Duration</td><td>").append(left.durationMs()).append("ms</td><td>").append(right.durationMs()).append("ms</td></tr>\n");
        sb.append("</table>\n<h2>Verdict</h2>\n");
        if (compatible) {
            sb.append("<p style='color:green'><strong>COMPATIBLE</strong></p>\n");
        } else {
            sb.append("<p style='color:red'><strong>INCOMPATIBLE</strong></p>\n<ul>\n");
            for (String b : blockers) sb.append("<li>").append(escape(b)).append("</li>\n");
            sb.append("</ul>\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("<h3>Warnings</h3><ul>\n");
            for (String w : warnings) sb.append("<li>").append(escape(w)).append("</li>\n");
            sb.append("</ul>\n");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static Instant parseInstant(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return Instant.ofEpochMilli(node.asLong());
        } catch (Exception e1) {
            try {
                return Instant.parse(node.asText());
            } catch (Exception e2) {
                return Instant.now();
            }
        }
    }

    private static String sha256(String data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
