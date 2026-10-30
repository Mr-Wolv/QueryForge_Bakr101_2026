package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses raw EXPLAIN (ANALYZE, BUFFERS) text plans into a structured, frontend-friendly tree.
 *
 * <p>Handles parallel plans, joins, CTEs, materialization, wrapped lines, and unknown node types.
 * Each node preserves raw text for fallback rendering.
 */
@Service
public class PlanParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NODE_TYPE_PATTERN = Pattern.compile(
            "^\\s*(?:->\\s*)?([A-Za-z][A-Za-z0-9\\s]*?(?:\\s+on\\s+\\w+)?)(?:\\s*:\\s*|\\s*\\()"
    );

    public PlanNode parse(String raw) {
        List<String> lines = raw.lines().toList();
        List<PlanNode> allNodes = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();

        String currentType = null;
        int currentIndent = -1;
        Map<String, String> currentProps = new LinkedHashMap<>();
        StringBuilder currentRaw = new StringBuilder();
        Map<String, String> executionTimeProps = new LinkedHashMap<>();

        for (String line : lines) {
            if (line.isBlank()) continue;

            String trimmed = line.stripLeading();
            if (trimmed.startsWith("Settings:") || trimmed.startsWith("JIT:") || trimmed.startsWith("Trigger:")) {
                continue;
            }

            if (trimmed.startsWith("Planning Time:")) {
                appendProperties(executionTimeProps, Map.of(
                        "planningTimeMs", trimmed.replaceAll("[^0-9.]", "").strip()
                ));
                continue;
            }

            if (trimmed.startsWith("Execution Time:")) {
                appendProperties(executionTimeProps, Map.of(
                        "executionTimeMs", trimmed.replaceAll("[^0-9.]", "").strip()
                ));
                continue;
            }

            int indent = line.length() - line.stripLeading().length();
            PlanNode candidate = nodeFromLine(line, indent);

            if (currentType != null && indent > currentIndent) {
                String cType = candidate.type();
                if (cType.equals("unknown") || isPropertyLine(candidate.raw(), currentType)) {
                    appendProperties(currentProps, candidate.properties());
                    currentRaw.append('\n').append(candidate.raw());
                    continue;
                }
            }

            if (currentType != null) {
                allNodes.add(new PlanNode(currentType, currentProps, List.of(),
                        currentRaw.toString().strip()));
                indents.add(currentIndent);
            }

            currentType = candidate.type();
            currentIndent = indent;
            currentProps = new LinkedHashMap<>(candidate.properties());
            currentRaw.setLength(0);
            currentRaw.append(candidate.raw());
        }

        if (currentType != null) {
            allNodes.add(new PlanNode(currentType, currentProps, List.of(),
                    currentRaw.toString().strip()));
            indents.add(currentIndent);
        }

        // Build parent-child relationships by indentation
        int n = allNodes.size();
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < n; i++) children.add(new ArrayList<>());

        Deque<Integer> stack = new ArrayDeque<>();
        List<Integer> rootIndices = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int nodeIndent = indents.get(i);
            while (!stack.isEmpty() && nodeIndent <= indents.get(stack.peek())) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                rootIndices.add(i);
            } else {
                children.get(stack.peek()).add(i);
            }
            stack.push(i);
        }

        List<PlanNode> roots = new ArrayList<>();
        for (int idx : rootIndices) {
            roots.add(buildSubtree(allNodes, children, idx, executionTimeProps));
        }

        if (roots.size() == 1) {
            return roots.get(0);
        }
        if (roots.isEmpty() && !executionTimeProps.isEmpty()) {
            return new PlanNode("root", executionTimeProps, List.of(), raw.strip());
        }
        return new PlanNode("root", Map.of(), roots, raw.strip());
    }

    private PlanNode nodeFromLine(String line, int indent) {
        String trimmed = line.stripLeading();
        String headline = trimmed;
        Map<String, String> props = new LinkedHashMap<>();

        // Extract Buffers
        Matcher m = Pattern.compile("Buffers:\\s*(.+)").matcher(trimmed);
        if (m.find()) props.put("buffers", m.group(1).strip());

        // Heap Blocks
        m = Pattern.compile("Heap Blocks:\\s*(.+)").matcher(trimmed);
        if (m.find()) props.put("heapBlocks", m.group(1).strip());

        // Rows
        m = Pattern.compile("rows=(\\d+)").matcher(trimmed);
        if (m.find()) props.put("rows", m.group(1));

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
        if (m.find()) props.put("extra", m.group(1));

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

        // Type extraction
        String type = extractType(trimmed);
        if (type.isBlank()) type = "unknown";

        return new PlanNode(type, headline, props, new ArrayList<>(), indent);
    }

    private static String extractType(String headline) {
        // Strip arrow prefix
        int arrow = headline.indexOf("->");
        if (arrow >= 0) headline = headline.substring(arrow + 2).strip();

        // Remove leading digits like "1" or " 1 "
        headline = headline.replaceAll("^\\d+\\s+", "").strip();

        // Find end: first '(' or ':' after the type
        int paren = headline.indexOf('(');
        int colon = headline.indexOf(':');

        int end = -1;
        if (paren >= 0 && colon >= 0) end = Math.min(paren, colon);
        else if (paren >= 0) end = paren;
        else if (colon >= 0) end = colon;

        if (end >= 0) {
            String type = headline.substring(0, end).stripTrailing();
            return type.isBlank() ? "unknown" : type;
        }

        return headline.isBlank() ? "unknown" : headline;
    }

    private static boolean isPropertyLine(String raw, String parentType) {
        if (raw == null || raw.isBlank()) return false;
        String lower = raw.toLowerCase();
        // Nodes always contain '(' before any property values; property lines do not
        if (lower.contains("(cost=") || lower.contains("(actual time=")) return false;
        if (lower.startsWith("-> ")) return false;
        // Continuation of known property-bearing parents
        if (parentType == null) return false;
        return switch (parentType.split("\\s+")[0]) {
            case "Hash", "Merge", "Nested", "Append", "Result" -> true;
            default -> lower.matches(".*\\b(filter|cond|workers|batches|conflicts|wait|single|parent|sort|presorted|key|recheck|planned|launched|actual)\\b.*:.*");
        };
    }

    private static void appendProperties(Map<String, String> target, Map<String, String> source) {
        for (Map.Entry<String, String> e : source.entrySet()) {
            if (!target.containsKey(e.getKey())) target.put(e.getKey(), e.getValue());
        }
    }

    public record PlanNode(String type, Map<String, String> properties, List<PlanNode> children, String raw) {
        public PlanNode(String type, String headline, Map<String, String> properties, List<PlanNode> children, int indent) {
            this(type, properties, children, trimmedRaw(headline));
        }

        public PlanNode withChildren(List<PlanNode> newChildren) {
            return new PlanNode(type, properties, newChildren, raw);
        }

        private static String trimmedRaw(String headline) {
            return headline.replaceAll("^\\d+\\s+", "").strip();
        }

        public String headline() {
            if (properties().isEmpty()) return raw();
            StringBuilder sb = new StringBuilder(type());
            for (Map.Entry<String, String> e : properties().entrySet()) {
                sb.append("; ").append(e.getKey()).append("=").append(e.getValue());
            }
            return sb.toString();
        }
    }

    private record Frame(int indent, PlanNode node) {}

    private PlanNode buildSubtree(List<PlanNode> allNodes, List<List<Integer>> children, int idx,
                                  Map<String, String> executionTimeProps) {
        List<PlanNode> childNodes = new ArrayList<>();
        for (int childIdx : children.get(idx)) {
            childNodes.add(buildSubtree(allNodes, children, childIdx, executionTimeProps));
        }
        PlanNode node = allNodes.get(idx);
        Map<String, String> merged = new LinkedHashMap<>(executionTimeProps);
        merged.putAll(node.properties());
        return new PlanNode(node.type(), merged, childNodes, node.raw());
    }
}

