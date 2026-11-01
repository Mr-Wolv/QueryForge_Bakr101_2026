package com.bakr.queryforge.experiment;

import com.bakr.queryforge.experiment.runner.RunnerAdapter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ProcessExecutor {

    private final RunScheduler scheduler;
    private final RunEventPublisher events;

    public ProcessExecutor(RunScheduler scheduler, RunEventPublisher events) {
        this.scheduler = scheduler;
        this.events = events;
    }

    public ManagedProcess start(RunnerAdapter adapter, ExperimentTemplate template,
                                Map<String, String> parameters, Path workDir, String runId) throws Exception {
        Process process = adapter.execute(template, parameters, workDir);
        LogTailer stdout = new LogTailer(workDir.resolve("stdout.log"), runId, "stdout", events);
        LogTailer stderr = new LogTailer(workDir.resolve("stderr.log"), runId, "stderr", events);
        stdout.start();
        stderr.start();
        return new ManagedProcess(process, stdout, stderr);
    }

    public int await(ManagedProcess execution, long timeoutMs, Runnable onTimeout)
            throws InterruptedException {
        Process process = execution.process();
        ScheduledFuture<?> watchdog = scheduler.schedule(() -> {
            if (process.isAlive()) {
                onTimeout.run();
                process.destroyForcibly();
            }
        }, timeoutMs);
        try {
            return process.waitFor();
        } finally {
            watchdog.cancel(false);
        }
    }

    public void close(ManagedProcess execution) {
        if (execution == null) return;
        execution.stdout().stop();
        execution.stderr().stop();
    }

    public static final class ManagedProcess {
        private final Process process;
        private final LogTailer stdout;
        private final LogTailer stderr;

        private ManagedProcess(Process process, LogTailer stdout, LogTailer stderr) {
            this.process = process;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public Process process() {
            return process;
        }

        private LogTailer stdout() {
            return stdout;
        }

        private LogTailer stderr() {
            return stderr;
        }
    }

    static final class LogTailer {

        private static final long POLL_MS = 250;

        private final Path file;
        private final String runId;
        private final String stream;
        private final RunEventPublisher events;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final StringBuilder pending = new StringBuilder();
        private Thread thread;

        LogTailer(Path file, String runId, String stream, RunEventPublisher events) {
            this.file = file;
            this.runId = runId;
            this.stream = stream;
            this.events = events;
        }

        void start() {
            thread = new Thread(this::loop, "qf-log-tail");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running.set(false);
            if (thread != null) {
                try {
                    thread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void loop() {
            long offset = 0;
            while (running.get()) {
                offset = drain(offset);
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            drain(offset);
            flush();
        }

        private long drain(long offset) {
            if (!Files.isRegularFile(file)) return offset;
            try (InputStream input = Files.newInputStream(file)) {
                long skipped = 0;
                while (skipped < offset) {
                    long count = input.skip(offset - skipped);
                    if (count <= 0) break;
                    skipped += count;
                }
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    pending.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    offset += read;
                }
                emitLines();
            } catch (IOException ignored) {
                // Live output is best-effort; persisted logs remain available.
            }
            return offset;
        }

        private void emitLines() {
            int newline;
            while ((newline = pending.indexOf("\n")) >= 0) {
                String line = pending.substring(0, newline);
                pending.delete(0, newline + 1);
                if (!line.isBlank()) events.log(runId, stream, line);
            }
        }

        private void flush() {
            emitLines();
            if (!pending.isEmpty()) {
                events.log(runId, stream, pending.toString());
                pending.setLength(0);
            }
        }
    }
}
