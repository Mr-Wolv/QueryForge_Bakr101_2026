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
        assertThat(root.children().get(0).type()).isEqualTo("Buffers");
        assertThat(root.children().get(0).properties()).containsEntry("buffers", "shared hit=15");
        assertThat(root.children().get(1).type()).isEqualTo("Heap Blocks");
        assertThat(root.children().get(1).properties()).containsEntry("heapBlocks", "exact=1");
    }

    @Test
    void handlesParallelSeqScanWithWorkers() {
        String raw = """
                Parallel Seq Scan on products  (cost=0.00..100.00 rows=1000 width=64)
                  Buffers: shared read=8
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Parallel Seq Scan on products");
        assertThat(root.properties()).containsEntry("rows", "1000");
    }

    @Test
    void handlesHashJoinWithCondition() {
        String raw = """
                Hash Join  (cost=10.00..200.00 rows=50 width=32)
                  Hash Cond: a.id = b.id
                  ->  Seq Scan on a  (cost=0.00..50.00 rows=1000 width=16)
                  ->  Hash  (cost=30.00..30.00 rows=500 width=16)
                        ->  Seq Scan on b  (cost=0.00..30.00 rows=500 width=16)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Hash Join");
        assertThat(root.properties()).containsEntry("hash_cond", "a.id = b.id");
        assertThat(root.children()).hasSize(2);
    }

    @Test
    void handlesCteScanAndWrappedLines() {
        String raw = """
                CTE Scan on cte  (cost=0.00..150.00 rows=500 width=32)
                  Filter: (col IS NOT NULL)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("CTE Scan on cte");
        assertThat(root.properties()).containsEntry("filter", "(col IS NOT NULL)");
    }

    @Test
    void skipsSettingsAndExtractsExecutionTimes() {
        String raw = """
                Seq Scan on users  (cost=0.00..10.00 rows=100 width=32)
                Settings: enable_indexscan = on
                Planning Time: 5 ms
                Execution Time: 12 ms
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Seq Scan on users");
        assertThat(root.properties()).containsEntry("planningTimeMs", "5")
                .containsEntry("executionTimeMs", "12");
        assertThat(root.children()).isEmpty();
    }

    @Test
    void handlesBitmapHeapScanWithRecheck() {
        String raw = """
                Bitmap Heap Scan on products  (cost=20.00..150.00 rows=50 width=32)
                  Recheck Cond: (id > 100)
                  ->  Bitmap Index Scan on idx_products_id  (cost=0.00..20.00 rows=50 width=0)
                        Index Cond: (id > 100)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Bitmap Heap Scan on products");
        assertThat(root.properties()).containsEntry("recheck_cond", "(id > 100)");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).type()).isEqualTo("Bitmap Index Scan on idx_products_id");
    }

    @Test
    void handlesIncrementalSort() {
        String raw = """
                Incremental Sort  (cost=0.00..100.00 rows=500 width=32)
                  Sort Key: name
                  Presorted Key: name
                  ->  Seq Scan on users  (cost=0.00..80.00 rows=500 width=32)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Incremental Sort");
        assertThat(root.properties()).containsEntry("sort_key", "name");
        assertThat(root.properties()).containsEntry("presorted_key", "name");
    }

    @Test
    void handlesGatherNotMerge() {
        String raw = """
                Gather  (cost=100.00..200.00 rows=100 width=32) (actual time=1.000..5.000 rows=100 loops=1)
                  Workers Planned: 4
                  Workers Launched: 4
                  ->  Parallel Seq Scan on products  (cost=0.00..180.00 rows=25 width=32)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Gather");
        assertThat(root.properties()).containsEntry("workers_planned", "4");
        assertThat(root.children()).hasSize(1);
    }

    @Test
    void handlesResultNodeWithOneTimeFilter() {
        String raw = """
                Result  (cost=0.00..10.00 rows=1 width=4) (actual time=0.001..0.001 rows=1 loops=1)
                  One-Time Filter: (random() > 0.5)
                  ->  Result  (cost=0.00..0.00 rows=1 width=4)
                """;
        PlanNode root = parser.parse(raw);
        assertThat(root.type()).isEqualTo("Result");
        assertThat(root.properties()).containsEntry("one-time_filter", "(random() > 0.5)");
        assertThat(root.children()).hasSize(1);
    }
}
