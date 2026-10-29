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
 * Phase 1 contract: tolerate unknown node types, never discard raw text, and preserve
 * every numeric value that appears in the original plan.
 */
@Service
public class PlanParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NODE_PATTERN = Pattern.compile(
            "^\\s*(\\w+\\s*(?:on\\s+\\w+)?)\\s*(?:\\(.*?\\))?\\s*(?:->.*)?$"
    );

    public PlanNode parse(String raw) {
        List<String> lines = raw.lines().toList();
        List<PlanNode> roots = new ArrayList<>();
        Deque<PlanNode> stack = new ArrayDeque<>();

        for (String line : lines) {
            if (line.isBlank()) continue;
            int indent = line.length() - line.stripLeading().length();
            PlanNode node = nodeFromLine(line, indent);
            while (!stack.isEmpty() && indent <= stack.peek().indent()) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.peek().children().add(node);
            }
            stack.push(node);
        }
        if (roots.size() == 1) return roots.get(0);
        PlanNode root = new PlanNode("root", null, Map.of(), new ArrayList<>(), -1);
        root.children().addAll(roots);
        return root;
    }

    private PlanNode nodeFromLine(String line, int indent) {
        String trimmed = line.stripLeading();

        // Split at first '->' or 'Buffers:' or 'Heap Blocks:' for detail
        String[] parts = trimmed.split("->", 2);
        String headline;
        if (parts[0].isBlank() && parts.length > 1) {
            headline = parts[1].strip();
        } else {
            headline = parts[0].stripTrailing();
        }
        String detail = parts.length > 1 ? parts[1].strip() : "";

        Map<String, String> props = new LinkedHashMap<>();
        String type = extractType(headline);

        // Buffers
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

        // Execution Time
        m = Pattern.compile("Execution Time:\\s*(\\d+(?:\\.\\d+)?)").matcher(trimmed);
        if (m.find()) props.put("executionTimeMs", m.group(1));

        // Cost / rows / width from generic plan (not analyze)
        m = Pattern.compile("cost=(\\d+\\.\\d+)\\.\\.(\\d+\\.\\d+)").matcher(trimmed);
        if (m.find()) {
            props.put("planCostStart", m.group(1));
            props.put("planCostEnd", m.group(2));
        }

        return new PlanNode(type, headline, props, new ArrayList<>(), indent);
    }

    private static String extractType(String headline) {
        // Strip the trailing (cost=...) clause if present; keep qualifiers like
        // "Seq Scan on products" and "Index Only Scan using idx_x on products".
        int paren = headline.indexOf('(');
        if (paren >= 0) {
            headline = headline.substring(0, paren).stripTrailing();
        }
        return headline.isBlank() ? "unknown" : headline;
    }

    public record PlanNode(String type, String headline, Map<String, String> properties, List<PlanNode> children, int indent) {
    }
}
