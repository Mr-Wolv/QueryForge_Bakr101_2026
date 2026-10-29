package com.bakr.queryforge.experiment;

import java.time.Instant;

/**
 * Run lifecycle states.
 */
public enum ExperimentRunState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
