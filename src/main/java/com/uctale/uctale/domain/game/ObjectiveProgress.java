package com.uctale.uctale.domain.game;

import java.util.Objects;

public record ObjectiveProgress(
        ObjectiveProgressType type,
        int count,
        boolean value,
        String state
) {
    public ObjectiveProgress {
        Objects.requireNonNull(type, "objective progress type은 필수입니다.");
        switch (type) {
            case COUNT -> {
                if (count < 0 || value || state != null) throw new IllegalArgumentException("COUNT objective progress가 올바르지 않습니다.");
            }
            case BOOLEAN -> {
                if (count != 0 || state != null) throw new IllegalArgumentException("BOOLEAN objective progress가 올바르지 않습니다.");
            }
            case STATE_MATCH -> {
                if (count != 0 || value || state == null || state.isBlank()) throw new IllegalArgumentException("STATE_MATCH objective progress가 올바르지 않습니다.");
                state = state.trim();
            }
        }
    }

    public static ObjectiveProgress count(int count) {
        return new ObjectiveProgress(ObjectiveProgressType.COUNT, count, false, null);
    }

    public static ObjectiveProgress bool(boolean value) {
        return new ObjectiveProgress(ObjectiveProgressType.BOOLEAN, 0, value, null);
    }

    public static ObjectiveProgress state(String state) {
        return new ObjectiveProgress(ObjectiveProgressType.STATE_MATCH, 0, false, state);
    }
}
