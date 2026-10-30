package com.bakr.queryforge.experiment;

/** Raised when a run is not in a state that permits the requested transition. */
public class RunConflictException extends RuntimeException {

    public RunConflictException(String message) {
        super(message);
    }
}
