package com.bakr.queryforge.artifact;

import java.util.*;

public class PlanTreeBuilder {
    public PlanParser.PlanNode buildTree(List<PlanParser.PlanNode> allNodes, List<Integer> indents,
                                        Map<String, String> executionTimeProps) {
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

        List<PlanParser.PlanNode> roots = new ArrayList<>();
        for (int idx : rootIndices) {
            roots.add(buildSubtree(allNodes, children, idx, executionTimeProps));
        }

        String raw = "";
        // For compatibility with original behavior - raw will be reconstructed by caller if needed
        if (roots.size() == 1) {
            return roots.get(0);
        }
        if (roots.isEmpty() && !executionTimeProps.isEmpty()) {
            return new PlanParser.PlanNode("root", executionTimeProps, List.of(), "");
        }
        return new PlanParser.PlanNode("root", Map.of(), roots, "");
    }

    private PlanParser.PlanNode buildSubtree(List<PlanParser.PlanNode> allNodes, List<List<Integer>> children, int idx,
                                            Map<String, String> executionTimeProps) {
        List<PlanParser.PlanNode> childNodes = new ArrayList<>();
        for (int childIdx : children.get(idx)) {
            childNodes.add(buildSubtree(allNodes, children, childIdx, executionTimeProps));
        }
        PlanParser.PlanNode node = allNodes.get(idx);
        Map<String, String> merged = new LinkedHashMap<>(executionTimeProps);
        merged.putAll(node.properties());
        return new PlanParser.PlanNode(node.type(), merged, childNodes, node.raw());
    }
}
