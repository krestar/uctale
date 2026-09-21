package com.uctale.uctale.dto;

import java.time.LocalDateTime;

public record SessionSummaryResponse(
        Long sessionId,
        String title,
        int currentTurn,
        LocalDateTime updatedAt,
        String status,
        String statusMessage,
        boolean retryable,
        boolean canResume,
        Long retryAfterSeconds,
        String thumbnailUrl
) {}
