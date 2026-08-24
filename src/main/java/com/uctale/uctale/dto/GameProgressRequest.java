package com.uctale.uctale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GameProgressRequest(
        @NotNull(message = "세션 ID는 필수입니다.")
        @Positive(message = "세션 ID는 양수여야 합니다.")
        Long sessionId,

        @Positive(message = "선택지 ID는 양수여야 합니다.")
        int choiceId,

        @Positive(message = "기대 턴은 양수여야 합니다.")
        int expectedTurn
) {}
