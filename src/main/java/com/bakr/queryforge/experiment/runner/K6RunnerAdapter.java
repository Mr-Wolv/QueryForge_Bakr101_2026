package com.bakr.queryforge.experiment.runner;

import com.bakr.queryforge.config.InternalRunnerToken;
import com.bakr.queryforge.experiment.ExperimentTemplate;
import com.bakr.queryforge.measurement.Metrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Registered k6 runner facade; command creation, process lifecycle, and summary parsing
 * are isolated in dedicated collaborators.
 */
@Component
public class K6RunnerAdapter implements RunnerAdapter {

    public static final String SUMMARY_FILE = "k6-summary.json";

    private final InternalRunnerToken runnerToken;
    private final String baseUrl;
    private final K6CommandGenerator commandGenerator;
    private final K6ProcessRunner processRunner;
    private final K6SummaryParser summaryParser;

    @Autowired
    public K6RunnerAdapter(InternalRunnerToken runnerToken,
                           @Value("${queryforge.runner.base-url:http://127.0.0.1:8080}") String baseUrl,
                           K6CommandGenerator commandGenerator,
                           K6ProcessRunner processRunner,
                           K6SummaryParser summaryParser) {
        this.runnerToken = runnerToken;
        this.baseUrl = baseUrl;
        this.commandGenerator = commandGenerator;
        this.processRunner = processRunner;
        this.summaryParser = summaryParser;
    }

    public K6RunnerAdapter(InternalRunnerToken runnerToken) {
        this(runnerToken, "http://127.0.0.1:8080",
                new K6CommandGenerator(), new K6ProcessRunner(), new K6SummaryParser());
    }

    @Override
    public String name() {
        return "k6";
    }

    @Override
    public String description() {
        return "k6 constant-arrival-rate workload";
    }

    @Override
    public String version() {
        try {
            return processRunner.version(commandGenerator.resolveK6Binary());
        } catch (IOException e) {
            return "unavailable";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    @Override
    public String sourceFingerprint(String templateId) {
        Path script = commandGenerator.resolveScript(templateId);
        if (script == null || !Files.isRegularFile(script)) return "unavailable";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(script));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "unavailable";
        }
    }

    @Override
    public Process execute(ExperimentTemplate template, Map<String, String> parameters, Path workDir)
            throws IOException {
        List<String> command = commandGenerator.generate(template, parameters, workDir, baseUrl, runnerToken);
        return processRunner.start(command, workDir);
    }

    @Override
    public Path locateSummary(Path workDir, Map<String, String> parameters) {
        return summaryParser.locateSummary(workDir, parameters);
    }

    @Override
    public Metrics parseSummary(Path summary) throws IOException {
        return summaryParser.parse(summary);
    }

    Path resolveScript(String templateId) {
        return commandGenerator.resolveScript(templateId);
    }

    static Map<String, String> k6Environment(ExperimentTemplate template, Map<String, String> parameters) {
        return K6CommandGenerator.k6Environment(template, parameters);
    }

    static void drain(InputStream input, Path output, long cap) {
        K6ProcessRunner.drain(input, output, cap);
    }
}
