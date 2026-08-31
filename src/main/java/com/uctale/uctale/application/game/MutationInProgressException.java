package com.uctale.uctale.application.game;

public class MutationInProgressException extends RuntimeException {
    private final long retryAfterSeconds;

    public MutationInProgressException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
