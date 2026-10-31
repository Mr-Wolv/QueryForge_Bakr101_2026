package com.bakr.queryforge.artifact;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlanNodeFactory {
    private static final Pattern NODE_TYPE_PATTERN = Pattern.compile(
            "^\\s*(?:->\\s*)?([A-Za-z][A-Za-z0-9\\s]*?(?:\\s+on\\s+\\w+)?)(?:\\s*:\\s*|\\s*\\()"
    );

    public PlanParser.PlanNode createNode(String line, int indent) {
        String trimmed = line.stripLeading();
        String headline = trimmed;
        Map<String, String> props = new LinkedHashMap<>();

        // Extract Buffers
        Matcher m = Pattern.compile("Buffers:\\s*(.+)").matcher(trimmed);
        if (m.find()) {
            props.put("buffers", m.group(1).strip());
        }

        // Heap Blocks
        m = Pattern.compile("Heap Blocks:\\s*(.+)").matcher(trimmed);
        if (m.find()) {
            props.put("heapBlocks", m.group(1).strip());
        }

        // Rows
        m = Pattern.compile("rows=(\\d+)").matcher(trimmed);
        if (m.find()) {
            props.put("rows", m.group(1));
        }

        // Actual time
        m = Pattern.compile("actual time=(\\d+\\.\\d+)\\.\\.(\\d+\\.\\d+)").matcher(trimmed);
        if (m.find()) {
            props.put("actualStartMs", m.group(1));
            props.put("actualEndMs", m.group(2));
        }

        // Cost
        m = Pattern.compile("cost=(\\d+\\.\\d+)\\.\\.(\\d+\\.\\d+)").matcher(trimmed);
        if (m.find()) {
            props.put("planCostStart", m.group(1));
            props.put("planCostEnd", m.group(2));
        }

        // Wasted Bags
        m = Pattern.compile("(?:Batches|Rows Removed by Filter|Rows Removed by Index Recheck|Heap Fetches|Conflicts|Wait Events|Shared hit|Shared read|Local hit|Local read|Temp read|Temp written)=(\\S+)").matcher(trimmed);
        if (m.find()) {
            props.put("extra", m.group(1));
        }

        // Filter / Index Cond / Hash Cond / Join Filter / Subplans
        String[] detailKeys = {"Filter", "Index Cond", "Hash Cond", "Join Filter", "Rows Removed by Filter",
                "Rows Removed by Index Recheck", "Heap Fetches", "Single Copy", "Parent Relationship",
                "Parallel", "Recheck Cond", "One-Time Filter", "Workers Planned", "Workers Launched",
                "Sort Key", "Presorted Key", "Actual Total Time"};
        for (String key : detailKeys) {
            if (trimmed.contains(key + ":") || trimmed.contains(key + "=")) {
                int idx = trimmed.indexOf(key);
                if (idx >= 0) {
                    props.put(key.toLowerCase().replace(' ', '_'), trimmed.substring(idx + key.length()).replaceAll("^[:=\\s]+", "").strip());
                }
            }
        }

        String type = extractType(trimmed);
        if (type.isBlank()) {
            type = "unknown";
        }

        return new PlanParser.PlanNode(type, headline, props, new java.util.ArrayList<>(), indent);
    }

    public String extractType(String headline) {
        String h = headline;
        // Strip arrow prefix
        int arrow = h.indexOf("->");
        if (arrow >= 0) {
            h = h.substring(arrow + 2).strip();
        }

        // Remove leading digits like "1" or " 1 "
        h = h.replaceAll("^\\d+\\s+", "").strip();

        // Find end: first '(' or ':' after the type
        int paren = h.indexOf('(');
        int colon = h.indexOf(':');

        int end = -1;
        if (paren >= 0 && colon >= 0) {
            end = Math.min(paren, colon);
        } else if (paren >= 0) {
            end = paren;
        } else if (colon >= 0) {
            end = colon;
        }

        if (end >= 0) {
            String type = h.substring(0, end).stripTrailing();
            return type.isBlank() ? "unknown" : type;
        }

        return h.isBlank() ? "unknown" : h;
    }

    public boolean isPropertyLine(String raw, String parentType) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String lower = raw.toLowerCase();
        // Nodes always contain '(' before any property values; property lines do not
        if (lower.contains("(cost=") || lower.contains("(actual time=")) {
            return false;
        }
        if (lower.startsWith("-> ")) {
            return false;
        }
        if (parentType == null) {
            return false;
        }
        return switch (parentType.split("\\s+")[0]) {
            case "Hash", "Merge", "Nested", "Append", "Result" -> true;
            default -> lower.matches(".*\\b(filter|cond|workers|batches|conflicts|wait|single|parent|sort|presorted|key|recheck|planned|launched|actual)\\b.*:.*");
        };
    }

    public void appendProperties(Map<String, String> target, Map<String, String> source) {
        for (Map.Entry<String, String> e : source.entrySet()) {
            if (!target.containsKey(e.getKey())) {
                target.put(e.getKey(), e.getValue());
            }
        }
    }
}
