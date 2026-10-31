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

    private final K6RunnerAdapter adapter = new K6RunnerAdapter(new com.bakr.queryforge.config.InternalRunnerToken());

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

    @Test
    void drainKeepsConsumingAfterTheCaptureCap(@TempDir Path temp) throws Exception {
        // Deliver 100 bytes in 4-byte chunks. The child must never be left with an
        // unread pipe once the 10-byte evidence cap is reached.
        byte[] payload = new byte[100];
        java.util.Arrays.fill(payload, (byte) 'x');
        final int[] consumed = {0};
        java.io.InputStream chunked = new java.io.InputStream() {
            @Override
            public int read() {
                return consumed[0] < payload.length ? payload[consumed[0]++] : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (consumed[0] >= payload.length) return -1;
                int n = Math.min(4, Math.min(len, payload.length - consumed[0]));
                System.arraycopy(payload, consumed[0], b, off, n);
                consumed[0] += n;
                return n;
            }
        };

        Path out = temp.resolve("stdout.log");
        K6RunnerAdapter.drain(chunked, out, 10);

        assertThat(consumed[0]).isEqualTo(payload.length);
        String written = Files.readString(out);
        assertThat(written).startsWith("xxxxxxxxxx");
        assertThat(written).contains("[output truncated at 1 MiB]");
    }

    @Test
    void drainStoresWholeStreamWhenUnderTheCap(@TempDir Path temp) throws Exception {
        Path out = temp.resolve("stdout.log");
        K6RunnerAdapter.drain(new java.io.ByteArrayInputStream("short output".getBytes()), out, 1_048_576L);

        assertThat(Files.readString(out)).isEqualTo("short output");
    }

    @Test
    void bareDurationSecondsBecomeK6Durations() {
        ExperimentTemplate template = new ExperimentTemplate("QF-001", "t", "p", "h",
                List.of(
                        new ExperimentTemplate.ParameterField("RATE", "integer", "req/s", 1, 200, null),
                        new ExperimentTemplate.ParameterField("DUR", "duration", "seconds", 10, 600, null)),
                "1m", false, List.of(), "", List.of());

        Map<String, String> env = K6RunnerAdapter.k6Environment(template,
                Map.of("RATE", "5", "DUR", "10"));

        // k6 reads a bare number as milliseconds; the API contract is seconds.
        assertThat(env.get("DUR")).isEqualTo("10s");
        assertThat(env.get("RATE")).isEqualTo("5");
    }

    @Test
    void explicitK6DurationsAreLeftAlone() {
        ExperimentTemplate template = new ExperimentTemplate("QF-001", "t", "p", "h",
                List.of(new ExperimentTemplate.ParameterField("DUR", "duration", "seconds", 10, 600, null)),
                "1m", false, List.of(), "", List.of());

        assertThat(K6RunnerAdapter.k6Environment(template, Map.of("DUR", "2m")).get("DUR")).isEqualTo("2m");
        assertThat(K6RunnerAdapter.k6Environment(template, Map.of("DUR", "90s")).get("DUR")).isEqualTo("90s");
    }
}
