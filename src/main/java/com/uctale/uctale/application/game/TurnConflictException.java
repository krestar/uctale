package com.uctale.uctale.application.game;

public class TurnConflictException extends RuntimeException {

    public TurnConflictException(String message) {
        super(message);
    }

    public TurnConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
