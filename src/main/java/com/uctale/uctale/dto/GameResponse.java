package com.uctale.uctale.dto;

import java.util.List;

public record GameResponse(
        Long sessionId,
        int turnNumber,
        String title,
        String storyText,
        List<GameChoice> choices,
        String mainImageUrl
) {}
