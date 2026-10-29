package com.bakr.queryforge.artifact;

import com.bakr.queryforge.artifact.PlanParser.PlanNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanParserTest {

    private final PlanParser parser = new PlanParser();

    @Test
    void parsesSingleRootNode() {
        PlanNode root = parser.parse("Limit  (cost=0.00..1234.56 rows=100 width=32) (actual time=0.015..2.345 rows=100 loops=1)\n");
        assertThat(root.type()).isEqualTo("Limit");
        assertThat(root.properties()).containsEntry("planCostStart", "0.00")
                .containsEntry("planCostEnd", "1234.56")
                .containsEntry("rows", "100")
                .containsEntry("actualStartMs", "0.015")
                .containsEntry("actualEndMs", "2.345");
        assertThat(root.children()).isEmpty();
    }

    @Test
    void parsesNestedTree() {
        String raw = """
                Limit  (cost=0.00..1234.56 rows=100 width=32)
                  ->  Sort  (cost=0.00..1500.00 rows=5000 width=64)
                        ->  Seq Scan on products  (cost=0.00..800.00 rows=5000 width=64)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Limit");
        assertThat(root.children()).hasSize(1);
        PlanNode sort = root.children().get(0);
        assertThat(sort.type()).isEqualTo("Sort");
        assertThat(sort.properties()).containsEntry("rows", "5000");
        assertThat(sort.children()).hasSize(1);
        assertThat(sort.children().get(0).type()).isEqualTo("Seq Scan on products");
    }

    @Test
    void preservesUnknownNodeTypes() {
        String raw = "CustomNode (cost=0.00..100.00 rows=10 width=16)\n";
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("CustomNode");
        assertThat(root.properties()).containsEntry("planCostStart", "0.00");
    }

    @Test
    void extractsExecutionTime() {
        String raw = "Execution Time: 42 ms\n";
        PlanNode root = parser.parse(raw);
        assertThat(root.properties()).containsEntry("executionTimeMs", "42");
    }

    @Test
    void handlesGatherMergePrefix() {
        String raw = "Gather Merge  (cost=100.00..200.00 rows=50 width=32)\n";
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Gather Merge");
    }

    @Test
    void handlesIndexOnlyScanWithUsing() {
        String raw = "Index Only Scan using idx_products_sku on products  (cost=0.28..8.30 rows=1 width=16)\n";
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Index Only Scan using idx_products_sku on products");
    }

    @Test
    void parsesBuffersAndHeapBlocks() {
        String raw = """
                Index Scan using idx_name on users  (cost=0.28..8.30 rows=1 width=16)
                  Buffers: shared hit=15
                  Heap Blocks: exact=1
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0).type()).isEqualTo("Buffers: shared hit=15");
        assertThat(root.children().get(0).properties()).containsEntry("buffers", "shared hit=15");
        assertThat(root.children().get(1).type()).isEqualTo("Heap Blocks: exact=1");
        assertThat(root.children().get(1).properties()).containsEntry("heapBlocks", "exact=1");
    }
}
