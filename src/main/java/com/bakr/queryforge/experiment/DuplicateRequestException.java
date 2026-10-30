package com.bakr.queryforge.experiment;

/** Raised when an idempotency key has already been used. */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String clientRequestId) {
        super("client request id already used: " + clientRequestId);
    }
}
