package com.uctale.uctale.application.game;

public class GameStateSnapshotException extends IllegalStateException {

    public GameStateSnapshotException(String message) {
        super(message);
    }

    public GameStateSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
