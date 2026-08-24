package com.uctale.uctale.dto;

import java.util.List;

public record GameResponse(
        Long sessionId,
        String title,
        String storyText,
        List<GeminiResponse.Choice> choices,
        String mainImageUrl
) {}