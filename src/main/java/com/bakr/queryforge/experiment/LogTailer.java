package com.bakr.queryforge.experiment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LogTailer {

    private static final long POLL_MS = 250;

    private final Path file;
    private final String runId;
    private final String stream;
    private final RunEventBus events;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final StringBuilder pending = new StringBuilder();
    private Thread thread;

    public LogTailer(Path file, String runId, String stream, RunEventBus events) {
        this.file = file;
        this.runId = runId;
        this.stream = stream;
        this.events = events;
    }

    public void start() {
        thread = new Thread(this::loop, "qf-log-tail");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
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
        try (InputStream in = Files.newInputStream(file)) {
            long skipped = 0;
            while (skipped < offset) {
                long n = in.skip(offset - skipped);
                if (n <= 0) break;
                skipped += n;
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                pending.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                offset += read;
            }
            emitLines();
        } catch (IOException ignored) {
            // best-effort live output
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
