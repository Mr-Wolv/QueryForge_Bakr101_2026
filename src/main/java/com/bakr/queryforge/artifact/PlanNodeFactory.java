package com.bakr.queryforge.artifact;

import java.util.Map;

public class PlanNodeFactory {
    public PlanParser.PlanNode createNode(String line, int indent) {
        return createNode(new PlanTokenizer().tokenizeLine(line, indent));
    }

    public PlanParser.PlanNode createNode(PlanTokenizer.TokenizedLine token) {
        return new PlanParser.PlanNode(token.type(), token.headline(), token.properties(),
                new java.util.ArrayList<>(), token.indent());
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
