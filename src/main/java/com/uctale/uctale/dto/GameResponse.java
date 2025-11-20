package com.uctale.uctale.dto;

import java.util.List;

public record GameResponse(
        String storyText,
        List<GeminiResponse.Choice> choices, // 선택지는 그대로 재사용
        String mainImageUrl,                 // 생성된 배경+캐릭터 합성 또는 배경 이미지 URL
        String characterImageUrl             // (선택적) 캐릭터 초상화 URL (오프닝 때 사용)
) {}