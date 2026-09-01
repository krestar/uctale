package com.uctale.uctale.dto;

import java.util.List;

public record GameResponse(
        Long sessionId,
        int turnNumber,
        String title,
        String storyText,
        List<GameChoice> choices,
        String mainImageUrl,
        List<CharacterStat> characterStats,
        SkillCheckView latestSkillCheck
) {
    public GameResponse(
            Long sessionId,
            int turnNumber,
            String title,
            String storyText,
            List<GameChoice> choices,
            String mainImageUrl
    ) {
        this(sessionId, turnNumber, title, storyText, choices, mainImageUrl, List.of(), null);
    }

    public record CharacterStat(
            String key,
            int score,
            int modifier
    ) {}

    public record SkillCheckView(
            String statType,
            int rawRoll,
            int statModifier,
            int situationalModifier,
            int dc,
            int total,
            String outcome,
            int rulesetVersion
    ) {}
}
