package com.uctale.uctale.domain.game;

public record StorySummary(
        int sourceFromTurn,
        int sourceToTurn,
        int stateVersion,
        String text
) {
    public StorySummary {
        text = text == null ? "" : text.trim();
        if (text.isBlank()) {
            if (sourceFromTurn != 0 || sourceToTurn != 0 || stateVersion != 0) {
                throw new IllegalArgumentException("빈 summary는 source range/state version을 가질 수 없습니다.");
            }
        } else {
            if (sourceFromTurn < 1 || sourceToTurn < sourceFromTurn) {
                throw new IllegalArgumentException("summary source turn range가 올바르지 않습니다.");
            }
            if (stateVersion < sourceToTurn) {
                throw new IllegalArgumentException("summary stateVersion은 source range보다 과거일 수 없습니다.");
            }
        }
    }

    public static StorySummary empty() {
        return new StorySummary(0, 0, 0, "");
    }

    public boolean emptySummary() {
        return text.isBlank();
    }

    public boolean isBlank() {
        return text.isBlank();
    }
}
