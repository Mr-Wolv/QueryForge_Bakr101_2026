package com.bakr.queryforge.measurement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized measurement DTO extracted from k6 JSON artifacts.
 */
public record Metrics(
        long testRunDurationMs,
        Map<String, BigDecimal> iterationPercentiles,
        Map<String, BigDecimal> httpPercentiles,
        long iterationCount,
        double rps) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Metrics fromK6Node(com.fasterxml.jackson.databind.JsonNode node) {
        long duration = 0;
        Map<String, BigDecimal> it = new LinkedHashMap<>();
        Map<String, BigDecimal> http = new LinkedHashMap<>();
        long count = 0;
        double rate = 0;

        if (node != null && node.isObject()) {
            var state = node.get("state");
            if (state != null && state.isObject()) {
                duration = (long) Math.round(state.get("testRunDurationMs").asDouble(0));
            }
            var metrics = node.get("metrics");
            if (metrics != null && metrics.isObject()) {
                var dur = metrics.get("iteration_duration");
                if (dur != null && dur.isObject()) {
                    var v = dur.get("values");
                    if (v != null && v.isObject()) {
                        put(it, "p50", v.get("p(50)"));
                        put(it, "p95", v.get("p(95)"));
                        put(it, "p99", v.get("p(99)"));
                        put(it, "avg", v.get("avg"));
                        put(it, "min", v.get("min"));
                        put(it, "max", v.get("max"));
                    }
                }
                var req = metrics.get("http_req_duration");
                if (req != null && req.isObject()) {
                    var v = req.get("values");
                    if (v != null && v.isObject()) {
                        put(http, "p50", v.get("p(50)"));
                        put(http, "p95", v.get("p(95)"));
                        put(http, "p99", v.get("p(99)"));
                    }
                }
                var iters = metrics.get("iterations");
                if (iters != null && iters.isObject()) {
                    var v = iters.get("values");
                    if (v != null && v.isObject()) {
                        count = v.get("count").asLong(0);
                        rate = v.get("rate").asDouble(0);
                    }
                }
            }
        }

        return new Metrics(duration, it, http, count, rate);
    }

    public static Metrics empty() {
        return new Metrics(0, Map.of(), Map.of(), 0, 0.0);
    }

    /** Build normalized metrics from a manifest {@code summaryMetrics} map. */
    public static Metrics fromSummaryMap(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) return empty();
        Map<String, BigDecimal> iteration = new LinkedHashMap<>();
        Map<String, BigDecimal> http = new LinkedHashMap<>();
        for (String key : List.of("p50", "p95", "p99", "avg", "min", "max", "med")) {
            BigDecimal plain = decimal(summary.get(key));
            if (plain != null) iteration.put(key, plain);
            BigDecimal it = decimal(summary.get("iteration." + key));
            if (it != null) iteration.put(key, it);
            BigDecimal ht = decimal(summary.get("http." + key));
            if (ht != null) http.put(key, ht);
        }
        long duration = longValue(summary.get("testRunDurationMs"));
        long iterations = longValue(summary.get("iterations"));
        double rps = doubleValue(summary.get("rps"));
        return new Metrics(duration, iteration, http, iterations, rps);
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static void put(Map<String, BigDecimal> map, String key, com.fasterxml.jackson.databind.JsonNode node) {
        if (node != null && node.isNumber()) {
            map.put(key, node.decimalValue());
        }
    }
}
