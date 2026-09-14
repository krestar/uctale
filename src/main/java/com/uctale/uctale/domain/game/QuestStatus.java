package com.uctale.uctale.domain.game;

public enum QuestStatus {
    AVAILABLE,
    ACTIVE,
    COMPLETED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED;
    }
}
