package com.bakr.queryforge.experiment.runner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ProcessRunner {
    private ProcessRunner() {}

    public static void drain(InputStream in, Path out, long cap) {
        byte[] buf = new byte[8192];
        long written = 0;
        boolean truncated = false;
        try (java.io.OutputStream os = Files.newOutputStream(out,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (written < cap) {
                    int toWrite = (int) Math.min(n, cap - written);
                    os.write(buf, 0, toWrite);
                    written += toWrite;
                } else {
                    truncated = true;
                }
            }
            if (truncated) {
                os.write("\n[output truncated at 1 MiB]\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }
}
