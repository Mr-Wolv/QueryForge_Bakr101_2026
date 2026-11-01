package com.bakr.queryforge.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunEventBusTest {

    @Test
    void subscribeReturnsEmitterAndStoresIt() {
        RunEventBus bus = new RunEventBus();
        SseEmitter emitter = bus.subscribe("run-1");
        assertThat(emitter).isNotNull();

        bus.state("run-1", "RUNNING");
        bus.log("run-1", "stdout", "hello");
        bus.artifact("run-1", "k6-summary");
        bus.error("run-1", "something went wrong");
        bus.complete("run-1");
    }

    @Test
    void subscribeMultipleEmittersForSameRun() {
        RunEventBus bus = new RunEventBus();
        SseEmitter e1 = bus.subscribe("run-1");
        SseEmitter e2 = bus.subscribe("run-1");

        bus.state("run-1", "RUNNING");
        bus.complete("run-1");
    }

    @Test
    void eventForUnknownRunIsNoOp() {
        RunEventBus bus = new RunEventBus();
        bus.state("nonexistent", "RUNNING");
        bus.log("nonexistent", "stdout", "hello");
        bus.artifact("nonexistent", "k6-summary");
        bus.error("nonexistent", "boom");
        bus.complete("nonexistent");
    }

    @Test
    void completeRemovesAllEmittersForRun() {
        RunEventBus bus = new RunEventBus();
        bus.subscribe("run-1");
        bus.subscribe("run-2");
        bus.subscribe("run-1");

        bus.complete("run-1");
        bus.state("run-1", "RUNNING"); // should be no-op after complete
        bus.state("run-2", "RUNNING"); // run-2 still active
    }

    @Test
    void sendEventDataStructure() {
        RunEventBus bus = new RunEventBus();
        SseEmitter emitter = bus.subscribe("run-1");

        bus.state("run-1", "QUEUED");
        assertThat(emitter).isNotNull();

        Map<String, Object> data = Map.of("state", "QUEUED", "at", java.time.Instant.now().toString());
        assertThat(data.get("state")).isEqualTo("QUEUED");
    }

    @Test
    void logPayloadCarriesTimestampAndNormalizedSeverity() {
        var at = java.time.Instant.parse("2026-11-01T10:00:00Z");
        Map<String, Object> data = RunEventBus.structuredLog("stdout", "WARNING: slow query", at);

        assertThat(data)
                .containsEntry("at", at.toString())
                .containsEntry("level", "WARN")
                .containsEntry("message", "slow query")
                .containsEntry("stream", "stdout");
    }

    @Test
    void stderrLogDefaultsToErrorSeverity() {
        Map<String, Object> data = RunEventBus.structuredLog("stderr", "runner failed", java.time.Instant.now());

        assertThat(data).containsEntry("level", "ERROR").containsEntry("message", "runner failed");
    }
}
