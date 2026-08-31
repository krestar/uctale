package com.uctale.uctale.domain.action;

import java.util.Map;
import java.util.Objects;

public record AvailableAction(
        int legacyChoiceId,
        String token,
        ActionType type,
        int sourceTurn,
        Map<String, String> arguments,
        String displayText
) {
    public AvailableAction {
        if (legacyChoiceId <= 0) throw new IllegalArgumentException("choice ID는 양수여야 합니다.");
        Objects.requireNonNull(type, "action type은 필수입니다.");
        if (sourceTurn <= 0) throw new IllegalArgumentException("source turn은 양수여야 합니다.");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        if (displayText == null || displayText.isBlank()) throw new IllegalArgumentException("action 표시 문구는 필수입니다.");
    }

    public boolean isLegacy() {
        return token == null || token.isBlank();
    }
}
