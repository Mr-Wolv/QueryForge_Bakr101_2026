package com.bakr.queryforge.experiment.runner;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class K6ProcessRunner {

    public Process start(List<String> command, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
        Process process = processBuilder.start();
        long outputCap = 1_048_576L;
        Thread stdoutDrainer = new Thread(
                () -> drain(process.getInputStream(), workDir.resolve("stdout.log"), outputCap),
                "k6-stdout-drain");
        Thread stderrDrainer = new Thread(
                () -> drain(process.getErrorStream(), workDir.resolve("stderr.log"), outputCap),
                "k6-stderr-drain");
        stdoutDrainer.setDaemon(true);
        stderrDrainer.setDaemon(true);
        stdoutDrainer.start();
        stderrDrainer.start();
        return process;
    }

    public String version(String binary) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(binary, "version").redirectErrorStream(true).start();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "timeout";
        }
        String output = new String(process.getInputStream().readNBytes(512), StandardCharsets.UTF_8).strip();
        return process.exitValue() == 0 && !output.isEmpty()
                ? output.lines().findFirst().orElse("unknown")
                : "unavailable";
    }

    public static void drain(InputStream input, Path output, long cap) {
        byte[] buffer = new byte[8192];
        long written = 0;
        boolean truncated = false;
        try (var stream = Files.newOutputStream(output,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (written < cap) {
                    int accepted = (int) Math.min(count, cap - written);
                    stream.write(buffer, 0, accepted);
                    written += accepted;
                } else {
                    truncated = true;
                }
            }
            if (truncated) stream.write("\n[output truncated at 1 MiB]\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Process output is best-effort evidence.
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }
}
