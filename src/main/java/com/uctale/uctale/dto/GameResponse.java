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
        SkillCheckView latestSkillCheck,
        GameplayView gameplay
) {
    public GameResponse(
            Long sessionId,
            int turnNumber,
            String title,
            String storyText,
            List<GameChoice> choices,
            String mainImageUrl
    ) {
        this(sessionId, turnNumber, title, storyText, choices, mainImageUrl, List.of(), null, null);
    }

    public GameResponse(Long sessionId, int turnNumber, String title, String storyText, List<GameChoice> choices,
                        String mainImageUrl, List<CharacterStat> characterStats, SkillCheckView latestSkillCheck) {
        this(sessionId, turnNumber, title, storyText, choices, mainImageUrl, characterStats, latestSkillCheck, null);
    }

    public record GameplayView(int hp, int maxHp, int mp, int maxMp, java.util.List<ItemView> items,
                               java.util.List<RelationshipView> relationships, java.util.List<QuestView> quests,
                               java.util.List<StatusView> statuses, String combatStatus) {
        public GameplayView {
            items = java.util.List.copyOf(items);
            relationships = java.util.List.copyOf(relationships);
            quests = java.util.List.copyOf(quests);
            statuses = java.util.List.copyOf(statuses);
        }
    }

    public record ItemView(String id, int quantity, boolean equipped) {}
    public record RelationshipView(String npcId, int affinity) {}
    public record QuestView(String questId, String status) {}
    public record StatusView(String effectId, int remainingTurns) {}

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
