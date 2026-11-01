package com.bakr.queryforge.experiment;

import org.springframework.stereotype.Component;

@Component
public class RunEventPublisher {

    private final RunEventBus eventBus;

    public RunEventPublisher(RunEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void state(String runId, String state) {
        eventBus.state(runId, state);
    }

    public void log(String runId, String stream, String text) {
        eventBus.log(runId, stream, text);
    }

    public void artifact(String runId, String artifactId) {
        eventBus.artifact(runId, artifactId);
    }

    public void error(String runId, String message) {
        eventBus.error(runId, message);
    }

    public void complete(String runId) {
        eventBus.complete(runId);
    }
}
