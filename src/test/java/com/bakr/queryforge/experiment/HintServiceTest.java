package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ArtifactService;
import com.bakr.queryforge.measurement.Metrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HintServiceTest {

    private final HintService service = new HintService(new ArtifactService());

    @Test
    void generatesTailLatencyHintFromMetricRecordAccessors() {
        Metrics metrics = new Metrics(1_000,
                Map.of("p95", BigDecimal.TEN),
                Map.of("p99", BigDecimal.valueOf(25)),
                100, 10.0);

        List<HintService.Hint> hints = service.generate(
                "Seq Scan (cost=0.00..10.00 rows=10 width=4)", metrics);

        assertThat(hints).extracting(HintService.Hint::id).containsExactlyInAnyOrder("h1", "h5");
        assertThat(hints).filteredOn(hint -> hint.id().equals("h5"))
                .singleElement()
                .extracting(HintService.Hint::title)
                .isEqualTo("High p99 tail latency");
    }

    @Test
    void returnsNoHintsForBlankPlan() {
        assertThat(service.generate("  ", Metrics.empty())).isEmpty();
    }
}
