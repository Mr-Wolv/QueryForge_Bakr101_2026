package com.bakr.queryforge.experiment;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class RunScheduler {

    private final ScheduledExecutorService watchdog =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "qf-watchdog"));
    private final ExecutorService runnerPool =
            Executors.newCachedThreadPool(r -> daemon(r, "qf-runner"));

    public void submit(Runnable task) {
        runnerPool.submit(task);
    }

    public ScheduledFuture<?> schedule(Runnable task, long delayMs) {
        return watchdog.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        runnerPool.shutdownNow();
        watchdog.shutdownNow();
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
