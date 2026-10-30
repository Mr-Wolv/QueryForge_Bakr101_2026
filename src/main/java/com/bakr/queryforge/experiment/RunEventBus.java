package com.bakr.queryforge.experiment;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process fan-out for run server-sent events.
 *
 * <p>Events are named ({@code state}, {@code log}, {@code artifact}, {@code error})
 * so browser clients can subscribe explicitly. Subscribers that disappear are
 * removed on the next publish; terminal runs complete their emitters.
 */
@Component
public class RunEventBus {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> remove(runId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        return emitter;
    }

    public void state(String runId, String state) {
        send(runId, "state", Map.of("state", state, "at", java.time.Instant.now().toString()));
    }

    public void log(String runId, String stream, String text) {
        send(runId, "log", Map.of("stream", stream, "text", text,
                "at", java.time.Instant.now().toString()));
    }

    public void artifact(String runId, String artifactId) {
        send(runId, "artifact", Map.of("artifactId", artifactId,
                "at", java.time.Instant.now().toString()));
    }

    public void error(String runId, String message) {
        send(runId, "error", Map.of("message", message,
                "at", java.time.Instant.now().toString()));
    }

    public void complete(String runId) {
        List<SseEmitter> list = emitters.remove(runId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already closed
            }
        }
    }

    private void send(String runId, String name, Object data) {
        List<SseEmitter> list = emitters.get(runId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException | IllegalStateException e) {
                remove(runId, emitter);
            }
        }
    }

    private void remove(String runId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(runId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(runId);
    }
}
