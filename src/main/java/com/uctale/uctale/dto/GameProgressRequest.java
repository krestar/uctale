package com.uctale.uctale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record GameProgressRequest(
        @NotNull(message = "세션 ID는 필수입니다.")
        @Positive(message = "세션 ID는 양수여야 합니다.")
        Long sessionId,

        @Positive(message = "선택지 ID는 양수여야 합니다.")
        int choiceId,

        @Positive(message = "기대 턴은 양수여야 합니다.")
        int expectedTurn,

        String actionToken,
        String actionType,
        Integer sourceTurn,
        Map<String, String> arguments
) {
    public GameProgressRequest(Long sessionId, int choiceId, int expectedTurn) {
        this(sessionId, choiceId, expectedTurn, null, null, null, null);
    }
}
