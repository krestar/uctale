package com.uctale.uctale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
        @Size(max = 8, message = "행동 arguments는 8개 이하여야 합니다.")
        Map<@NotBlank(message = "행동 argument key는 비어 있을 수 없습니다.") @Size(max = 64, message = "행동 argument key는 64자 이하여야 합니다.") String,
                @NotNull(message = "행동 argument value는 null일 수 없습니다.") @Size(max = 256, message = "행동 argument value는 256자 이하여야 합니다.") String> arguments
) {
    public GameProgressRequest(Long sessionId, int choiceId, int expectedTurn) {
        this(sessionId, choiceId, expectedTurn, null, null, null, null);
    }
}
