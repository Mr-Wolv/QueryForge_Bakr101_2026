package com.bakr.queryforge.experiment.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class K6ResultParser {
    private K6ResultParser() {}

    public static Path locateSummary(Path workDir, Map<String, String> parameters) {
        Path direct = workDir.resolve(K6RunnerAdapter.SUMMARY_FILE);
        if (Files.isRegularFile(direct)) return direct;
        String out = parameters.getOrDefault("OUT", "k6-run");
        Path nested = workDir.resolve("benchmark").resolve("results").resolve(out + ".json");
        if (Files.isRegularFile(nested)) return nested;
        Path legacy = workDir.resolve(out + ".json");
        if (Files.isRegularFile(legacy)) return legacy;
        return null;
    }
}
