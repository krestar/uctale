package com.uctale.uctale.dto;

import java.util.List;

// Gemini가 반환할 전체 JSON 구조
public record GeminiResponse(
        String title,
        String story_text,
        List<Choice> choices,
        VisualAssets visual_assets
) {
    // 내부용: 선택지 구조
    public record Choice(
            int id,
            String text
    ) {}

    // 내부용: 시각 자료 프롬프트 구조
    public record VisualAssets(
            String background,       // 오프닝용 배경
            List<String> characters, // 등장인물/몬스터 (GameInit에서는 안쓸 수도 있지만 구조 통일)
            List<String> assets      // 일반 사물 (나중에 사용)
    ) {}
}