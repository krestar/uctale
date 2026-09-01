package com.uctale.uctale.application.narrative;

public class InvalidNarrativeResponseException extends RuntimeException {
    public InvalidNarrativeResponseException(String message) {
        super(message);
    }

    public InvalidNarrativeResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
