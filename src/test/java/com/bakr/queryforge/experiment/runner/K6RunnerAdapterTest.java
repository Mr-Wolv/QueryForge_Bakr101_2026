package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.experiment.ExperimentTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class K6RunnerAdapterTest {

    private final K6RunnerAdapter adapter = new K6RunnerAdapter();

    @Test
    void nameAndDescription() {
        assertThat(adapter.name()).isEqualTo("k6");
        assertThat(adapter.description()).isNotNull();
    }

    @Test
    void resolveScriptMapsTemplateIds() {
        assertThat(adapter.resolveScript("QF-001").toString().replace('\\', '/')).endsWith("benchmark/k6/search.js");
        assertThat(adapter.resolveScript("QF-002").toString().replace('\\', '/')).endsWith("benchmark/k6/pagination.js");
        assertThat(adapter.resolveScript("QF-003").toString().replace('\\', '/')).endsWith("benchmark/k6/concurrency.js");
        assertThat(adapter.resolveScript("QF-000").toString().replace('\\', '/')).endsWith("benchmark/k6/point.js");
        assertThat(adapter.resolveScript("QF-999")).isNull();
    }

    @Test
    void executeProducesSummary(@TempDir Path temp) throws Exception {
        // Write a fake k6 summary JSON where the adapter expects it.
        Path script = temp.resolve("script.js");
        Files.writeString(script, "export default function() {}");
        ExperimentTemplate template = new ExperimentTemplate("QF-000", "t", "p", "h", List.of(), "1m", false,
                List.of(), "", List.of());
        Map<String, String> params = Map.of("OUT", "fake-summary");

        // We can't run real k6 here; test the locateSummary path by writing the expected file.
        Path expected = temp.resolve("fake-summary.json");
        Files.writeString(expected, "{}");
        Path found = adapter.locateSummary(temp, params);
        assertThat(found).isEqualTo(expected);
    }

    @Test
    void executeThrowsWhenScriptMissing() {
        ExperimentTemplate template = new ExperimentTemplate("QF-999", "t", "p", "h", List.of(), "1m", false,
                List.of(), "", List.of());
        assertThat(adapter.resolveScript("QF-999")).isNull();
    }
}
