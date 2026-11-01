package com.bakr.queryforge.experiment;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Component
public class ComparisonReportRenderer {

    private final TemplateEngine htmlTemplateEngine;
    private final TemplateEngine markdownTemplateEngine;

    public ComparisonReportRenderer() {
        htmlTemplateEngine = templateEngine(".html", TemplateMode.HTML);
        markdownTemplateEngine = templateEngine(".md", TemplateMode.TEXT);
    }

    public String buildMarkdown(ExperimentRun left, ExperimentRun right,
                                CompatibilityVerdict verdict, Map<String, MetricDelta> deltas) {
        return markdownTemplateEngine.process("comparison-report", context(left, right, verdict, deltas));
    }

    public String buildHtml(ExperimentRun left, ExperimentRun right,
                            CompatibilityVerdict verdict, Map<String, MetricDelta> deltas) {
        return htmlTemplateEngine.process("comparison-report", context(left, right, verdict, deltas));
    }

    private static TemplateEngine templateEngine(String suffix, TemplateMode mode) {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver(suffix, mode));
        return templateEngine;
    }

    private static ClassLoaderTemplateResolver resolver(String suffix, TemplateMode mode) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        return resolver;
    }

    private static Context context(ExperimentRun left, ExperimentRun right,
                                   CompatibilityVerdict verdict, Map<String, MetricDelta> deltas) {
        Context context = new Context(Locale.ROOT);
        context.setVariable("title", "Comparison " + left.id() + " vs " + right.id());
        context.setVariable("leftId", left.id());
        context.setVariable("rightId", right.id());
        context.setVariable("leftTemplate", left.templateId());
        context.setVariable("rightTemplate", right.templateId());
        context.setVariable("leftState", left.state());
        context.setVariable("rightState", right.state());
        context.setVariable("leftDuration", left.durationMs());
        context.setVariable("rightDuration", right.durationMs());
        context.setVariable("compatible", verdict.compatible());
        context.setVariable("blockers", verdict.blockers());
        context.setVariable("warnings", verdict.warnings());
        context.setVariable("differences", verdict.diff().entrySet().stream()
                .map(entry -> Map.of("key", entry.getKey(), "value", entry.getValue()))
                .toList());
        context.setVariable("deltas", deltas.entrySet().stream().map(entry -> {
            MetricDelta delta = entry.getValue();
            return Map.of(
                    "metric", entry.getKey(),
                    "left", plain(delta.left()) + " " + delta.unit(),
                    "right", plain(delta.right()) + " " + delta.unit(),
                    "delta", plain(delta.delta()),
                    "percent", delta.deltaPercent() == null ? "n/a" : plain(delta.deltaPercent()) + "%"
            );
        }).toList());
        return context;
    }

    private static String plain(java.math.BigDecimal value) {
        if (value == null) return "n/a";
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

}
