package com.bakr.queryforge.artifact;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlanParser {

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
            PlanNode candidate = nodeFactory.createNode(tokenizer.tokenizeLine(line, indent));

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

        return treeBuilder.buildTree(allNodes, indents, executionTimeProps, raw);
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

}
