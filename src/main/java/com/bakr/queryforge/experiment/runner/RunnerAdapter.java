package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.experiment.ExperimentTemplate;
import com.bakr.queryforge.measurement.Metrics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * A named, registered benchmark adapter that can be executed safely.
 *
 * <p>Phase 2 contract: adapters are allow-listed in code, not supplied by the client.
 * Parameters are typed and validated before invocation.
 */
public interface RunnerAdapter {

    String name();

    String description();

    default String version() {
        return "unknown";
    }

    default String sourceFingerprint(String templateId) {
        return "unknown";
    }

    /**
     * Execute the benchmark with the given template and validated parameters.
     *
     * @param template   the experiment template
     * @param parameters validated parameters (key = env var name, value = string)
     * @param workDir    working directory for the benchmark process
     * @return the started process handle (caller is responsible for lifecycle)
     * @throws IOException          if the process fails or produces no output
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    Process execute(ExperimentTemplate template, Map<String, String> parameters, Path workDir)
            throws IOException, InterruptedException;

    /**
     * Locate the runner's normalized summary after the process has exited.
     *
     * @return summary path, or {@code null} when this adapter does not produce one
     */
    default Path locateSummary(Path workDir, Map<String, String> parameters) {
        return null;
    }

    Metrics parseSummary(Path summary) throws IOException;
}
