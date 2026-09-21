package com.uctale.uctale.dto;

import java.time.LocalDateTime;

public record SessionResumeResponse(
        Long sessionId,
        String title,
        int turnNumber,
        LocalDateTime updatedAt,
        String status,
        String statusMessage,
        boolean retryable,
        boolean canProgress,
        Long retryAfterSeconds,
        Integer canonicalStateTurn,
        GameResponse game
) {}
