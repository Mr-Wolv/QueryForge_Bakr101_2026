package com.bakr.queryforge.experiment;

import java.math.BigDecimal;
import java.util.Map;

public final class ComparisonReportRenderer {
    private ComparisonReportRenderer() {}

    public static String buildMarkdown(ExperimentRun left, ExperimentRun right,
                                      CompatibilityVerdict verdict, Map<String, MetricDelta> deltas) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Comparison Report\n\n");
        sb.append("| Field | Left | Right |\n");
        sb.append("|-------|------|-------|\n");
        sb.append("| Run ID | `").append(left.id()).append("` | `").append(right.id()).append("` |\n");
        sb.append("| Template | ").append(left.templateId()).append(" | ").append(right.templateId()).append(" |\n");
        sb.append("| State | ").append(left.state()).append(" | ").append(right.state()).append(" |\n");
        sb.append("| Duration | ").append(left.durationMs()).append("ms | ").append(right.durationMs()).append("ms |\n\n");

        sb.append("## Verdict\n\n");
        if (verdict.compatible()) {
            sb.append("**COMPATIBLE** — runs can be compared.\n\n");
        } else {
            sb.append("**INCOMPATIBLE** — runs cannot be meaningfully compared. ");
            sb.append("Deltas below are shown for diagnosis, not as a controlled result.\n\n");
            sb.append("### Blockers\n\n");
            for (String b : verdict.blockers()) sb.append("- ").append(b).append("\n");
            sb.append("\n");
        }

        if (!verdict.warnings().isEmpty()) {
            sb.append("### Warnings\n\n");
            for (String w : verdict.warnings()) sb.append("- ").append(w).append("\n");
            sb.append("\n");
        }

        if (!verdict.diff().isEmpty()) {
            sb.append("## Differences\n\n");
            for (var e : verdict.diff().entrySet()) {
                sb.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append("\n");
            }
            sb.append("\n");
        }

        if (!deltas.isEmpty()) {
            sb.append("## Metric Deltas\n\n");
            sb.append("| Metric | Left | Right | Delta | Delta % |\n");
            sb.append("|--------|------|-------|-------|---------|\n");
            for (var e : deltas.entrySet()) {
                MetricDelta d = e.getValue();
                sb.append("| ").append(e.getKey()).append(" | ")
                        .append(plain(d.left())).append(" ").append(d.unit()).append(" | ")
                        .append(plain(d.right())).append(" ").append(d.unit()).append(" | ")
                        .append(plain(d.delta())).append(" | ")
                        .append(d.deltaPercent() == null ? "n/a" : plain(d.deltaPercent()) + "%")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## Reproduction\n\n");
        sb.append("Both runs are reproducible from their stored manifests and artifacts.\n");
        return sb.toString();
    }

    public static String buildHtml(ExperimentRun left, ExperimentRun right,
                                   CompatibilityVerdict verdict, Map<String, MetricDelta> deltas) {
        String md = buildMarkdown(left, right, verdict, deltas);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Comparison ")
                .append(escape(left.id())).append(" vs ").append(escape(right.id()))
                .append("</title></head><body>\n");
        sb.append("<h1>Comparison: ").append(escape(left.id())).append(" vs ")
                .append(escape(right.id())).append("</h1>\n");
        sb.append("<table border='1' cellpadding='6' cellspacing='0'>\n")
                .append("<tr><th>Field</th><th>Left</th><th>Right</th></tr>\n");
        sb.append("<tr><td>Template</td><td>").append(escape(left.templateId())).append("</td><td>")
                .append(escape(right.templateId())).append("</td></tr>\n");
        sb.append("<tr><td>State</td><td>").append(escape(left.state())).append("</td><td>")
                .append(escape(right.state())).append("</td></tr>\n");
        sb.append("<tr><td>Duration</td><td>").append(left.durationMs()).append("ms</td><td>")
                .append(right.durationMs()).append("ms</td></tr>\n");
        sb.append("</table>\n<h2>Verdict</h2>\n");
        if (verdict.compatible()) {
            sb.append("<p style='color:green'><strong>COMPATIBLE</strong></p>\n");
        } else {
            sb.append("<p style='color:#b00020'><strong>INCOMPATIBLE</strong></p>\n<ul>\n");
            for (String b : verdict.blockers()) sb.append("<li>").append(escape(b)).append("</li>\n");
            sb.append("</ul>\n");
        }
        if (!verdict.warnings().isEmpty()) {
            sb.append("<h3>Warnings</h3><ul>\n");
            for (String w : verdict.warnings()) sb.append("<li>").append(escape(w)).append("</li>\n");
            sb.append("</ul>\n");
        }
        if (!deltas.isEmpty()) {
            sb.append("<h3>Metric Deltas</h3><table border='1' cellpadding='6' cellspacing='0'>\n")
                    .append("<tr><th>Metric</th><th>Left</th><th>Right</th><th>Delta</th><th>Delta %</th></tr>\n");
            for (var e : deltas.entrySet()) {
                MetricDelta d = e.getValue();
                sb.append("<tr><td>").append(escape(e.getKey())).append("</td><td>")
                        .append(plain(d.left())).append(" ").append(escape(d.unit())).append("</td><td>")
                        .append(plain(d.right())).append(" ").append(escape(d.unit())).append("</td><td>")
                        .append(plain(d.delta())).append("</td><td>")
                        .append(d.deltaPercent() == null ? "n/a" : plain(d.deltaPercent()) + "%")
                        .append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }
        sb.append("<pre style='display:none'>").append(escape(md)).append("</pre>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String plain(BigDecimal value) {
        if (value == null) return "n/a";
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
