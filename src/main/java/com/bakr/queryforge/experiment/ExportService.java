package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.artifact.RunArtifact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates Markdown and HTML export reports for experiment runs.
 */
@Service
public class ExportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExperimentRunService runs;
    private final ExperimentTemplateService templates;
    private final ArtifactService artifactService;

    public ExportService(ExperimentRunService runs, ExperimentTemplateService templates,
                         ArtifactService artifactService) {
        this.runs = runs;
        this.templates = templates;
        this.artifactService = artifactService;
    }

    public String exportMarkdown(String runId) throws IOException {
        ExperimentRun run = runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        ExperimentTemplate template = templates.findById(run.templateId()).orElse(null);

        StringBuilder sb = new StringBuilder();
        sb.append("# Experiment Run: ").append(run.id()).append("\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Run ID | `").append(run.id()).append("` |\n");
        sb.append("| Template | ").append(run.templateId()).append(" — ").append(template != null ? template.title() : "unknown").append(" |\n");
        sb.append("| State | ").append(run.state()).append(" |\n");
        sb.append("| Started | ").append(run.startedAt()).append(" |\n");
        sb.append("| Duration | ").append(run.durationMs()).append("ms |\n");

        if (template != null) {
            sb.append("\n## Hypothesis\n\n").append(template.hypothesis()).append("\n");
        }

        sb.append("\n## Metrics\n\n");
        appendMetrics(sb, run.metrics());

        sb.append("\n## Artifacts\n\n");
        for (RunArtifact a : run.artifacts()) {
            sb.append("- **").append(a.kind()).append("** (").append(a.contentType()).append("): ").append(a.location()).append("\n");
        }
        if (run.artifacts().isEmpty()) {
            sb.append("(none)\n");
        }

        sb.append("\n## Labels\n\n");
        if (run.labels() != null && !run.labels().isEmpty()) {
            for (var e : run.labels().entrySet()) {
                sb.append("- ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
            }
        } else {
            sb.append("(none)\n");
        }

        return sb.toString();
    }

    public String exportHtml(String runId) throws IOException {
        String md = exportMarkdown(runId);
        // Minimal HTML: wrap markdown-ish lines in HTML
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Run ").append(runId).append("</title></head><body>\n");
        sb.append("<h1>Experiment Run: ").append(escape(runId)).append("</h1>\n");
        sb.append("<pre>\n").append(escape(md)).append("\n</pre>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendMetrics(StringBuilder sb, com.bakr.queryforge.measurement.Metrics metrics) {
        if (metrics == null || metrics == com.bakr.queryforge.measurement.Metrics.empty()) {
            sb.append("(no metrics)\n");
            return;
        }
        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Duration | ").append(metrics.testRunDurationMs()).append("ms |\n");
        sb.append("| Iterations | ").append(metrics.iterationCount()).append(" |\n");
        sb.append("| RPS | ").append(metrics.rps()).append(" |\n\n");

        if (!metrics.iterationPercentiles().isEmpty()) {
            sb.append("### Iteration Duration\n\n");
            sb.append("| Percentile | ms |\n");
            sb.append("|------------|----|\n");
            for (var e : metrics.iterationPercentiles().entrySet()) {
                sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n");
        }
        if (!metrics.httpPercentiles().isEmpty()) {
            sb.append("### HTTP Request Duration\n\n");
            sb.append("| Percentile | ms |\n");
            sb.append("|------------|----|\n");
            for (var e : metrics.httpPercentiles().entrySet()) {
                sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n");
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
