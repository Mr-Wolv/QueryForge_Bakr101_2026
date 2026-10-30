package com.bakr.queryforge.experiment;

import java.util.List;
import java.util.Map;

/** Raised when a parameter is missing, unknown, malformed, or outside its allow-list. */
public class ParameterValidationException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public ParameterValidationException(Map<String, List<String>> errors) {
        super("Invalid experiment parameters");
        this.errors = errors;
    }

    public Map<String, List<String>> errors() {
        return errors;
    }
}
