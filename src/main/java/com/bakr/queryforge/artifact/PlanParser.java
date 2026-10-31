package com.bakr.queryforge.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlanParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PlanTokenizer tokenizer = new PlanTokenizer();
    private final PlanNodeFactory nodeFactory = new PlanNodeFactory();
    private final PlanTreeBuilder treeBuilder = new PlanTreeBuilder();

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
            if (line.isBlank()) {
                continue;
            }

            String trimmed = line.stripLeading();
            if (trimmed.startsWith("Settings:") || trimmed.startsWith("JIT:") || trimmed.startsWith("Trigger:")) {
                continue;
            }

            if (trimmed.startsWith("Planning Time:")) {
                nodeFactory.appendProperties(executionTimeProps, Map.of(
                        "planningTimeMs", trimmed.replaceAll("[^0-9.]", "").strip()
                ));
                continue;
            }

            if (trimmed.startsWith("Execution Time:")) {
                nodeFactory.appendProperties(executionTimeProps, Map.of(
                        "executionTimeMs", trimmed.replaceAll("[^0-9.]", "").strip()
                ));
                continue;
            }

            int indent = line.length() - line.stripLeading().length();
            PlanNode candidate = nodeFromLine(line, indent);

            if (currentType != null && indent > currentIndent) {
                String cType = candidate.type();
                if (cType.equals("unknown") || nodeFactory.isPropertyLine(candidate.raw(), currentType)) {
                    nodeFactory.appendProperties(currentProps, candidate.properties());
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

        int n = allNodes.size();
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            children.add(new ArrayList<>());
        }

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
        return nodeFactory.createNode(line, indent);
    }

    private static String extractType(String headline) {
        return new PlanNodeFactory().extractType(headline);
    }

    private static boolean isPropertyLine(String raw, String parentType) {
        return new PlanNodeFactory().isPropertyLine(raw, parentType);
    }

    private static void appendProperties(Map<String, String> target, Map<String, String> source) {
        new PlanNodeFactory().appendProperties(target, source);
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
            if (properties().isEmpty()) {
                return raw();
            }
            StringBuilder sb = new StringBuilder(type());
            for (Map.Entry<String, String> e : properties().entrySet()) {
                sb.append("; ").append(e.getKey()).append("=").append(e.getValue());
            }
            return sb.toString();
        }
    }

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
