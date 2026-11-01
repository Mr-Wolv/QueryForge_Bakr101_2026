package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.measurement.Metrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class K6SummaryParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Path locateSummary(Path workDir, Map<String, String> parameters) {
        Path direct = workDir.resolve(K6RunnerAdapter.SUMMARY_FILE);
        if (Files.isRegularFile(direct)) return direct;

        String output = parameters.getOrDefault("OUT", "k6-run");
        Path nested = workDir.resolve("benchmark").resolve("results").resolve(output + ".json");
        if (Files.isRegularFile(nested)) return nested;
        Path legacy = workDir.resolve(output + ".json");
        return Files.isRegularFile(legacy) ? legacy : null;
    }

    public Metrics parse(Path summary) throws IOException {
        return Metrics.fromK6Node(MAPPER.readTree(summary.toFile()));
    }
}
