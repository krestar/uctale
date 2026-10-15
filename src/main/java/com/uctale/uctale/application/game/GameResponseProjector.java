package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.CharacterStats;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StatType;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameResponse;

import java.util.Arrays;
import java.util.List;

public final class GameResponseProjector {
    private GameResponseProjector() {}

    public static GameResponse project(
            Long sessionId,
            int turnNumber,
            String title,
            String storyText,
            List<GameChoice> choices,
            String imageUrl,
            GameState gameState,
            SkillCheckResult skillCheckResult
    ) {
        return new GameResponse(
                sessionId,
                turnNumber,
                title,
                storyText,
                choices,
                imageUrl,
                projectStats(gameState),
                projectSkillCheck(skillCheckResult),
                projectGameplay(gameState)
        );
    }

    public static GameResponse projectReplay(
            Long sessionId,
            int turnNumber,
            String title,
            String storyText,
            List<GameChoice> choices,
            String imageUrl,
            GameState gameState,
            GamePersistenceService.SkillCheckAudit skillCheckAudit
    ) {
        GameState exactReplayState = gameState != null && gameState.turnNumber() == turnNumber ? gameState : null;
        return new GameResponse(
                sessionId,
                turnNumber,
                title,
                storyText,
                choices,
                imageUrl,
                projectStats(exactReplayState),
                projectSkillCheckAudit(skillCheckAudit),
                projectGameplay(exactReplayState)
        );
    }

    private static GameResponse.GameplayView projectGameplay(GameState state) {
        if (state == null) return null;
        var vitals = state.playerCharacter().vitals();
        var equipped = state.inventory().equipment().slots().values();
        var items = state.inventory().items().values().stream()
                .map(item -> new GameResponse.ItemView(item.id(), item.quantity(), equipped.contains(item.id())))
                .toList();
        var relationships = state.relationshipState().relationships().values().stream()
                .map(relation -> new GameResponse.RelationshipView(relation.npc().instanceId(), relation.affinity()))
                .toList();
        var quests = state.questState().quests().values().stream()
                .map(quest -> new GameResponse.QuestView(quest.definitionId(), quest.status().name()))
                .toList();
        var statuses = vitals.statusEffects().values().stream()
                .map(status -> new GameResponse.StatusView(status.definitionId(), status.remainingTurns()))
                .toList();
        String combat = state.combatEncounter() == null ? null : state.combatEncounter().status().name();
        return new GameResponse.GameplayView(
                vitals.hp().current(), vitals.hp().max(), vitals.mp().current(), vitals.mp().max(),
                items, relationships, quests, statuses, combat
        );
    }

    private static List<GameResponse.CharacterStat> projectStats(GameState gameState) {
        if (gameState == null) return List.of();
        CharacterStats stats = gameState.playerCharacter().stats();
        return Arrays.stream(StatType.values())
                .map(statType -> new GameResponse.CharacterStat(
                        statType.name(),
                        stats.score(statType),
                        stats.modifier(statType)
                ))
                .toList();
    }

    private static GameResponse.SkillCheckView projectSkillCheck(SkillCheckResult result) {
        if (result == null) return null;
        return new GameResponse.SkillCheckView(
                result.statType().name(),
                result.rawRoll(),
                result.statModifier(),
                result.situationalModifier(),
                result.dc(),
                result.total(),
                result.outcome().name(),
                result.rulesetVersion()
        );
    }

    private static GameResponse.SkillCheckView projectSkillCheckAudit(GamePersistenceService.SkillCheckAudit audit) {
        if (audit == null) return null;
        if (audit.statType() == null
                || audit.rawRoll() == null
                || audit.statModifier() == null
                || audit.situationalModifier() == null
                || audit.dc() == null
                || audit.total() == null
                || audit.outcome() == null
                || audit.rulesetVersion() == null) {
            throw new IllegalStateException("저장된 Skill Check audit이 불완전합니다.");
        }
        return new GameResponse.SkillCheckView(
                audit.statType(),
                audit.rawRoll(),
                audit.statModifier(),
                audit.situationalModifier(),
                audit.dc(),
                audit.total(),
                audit.outcome(),
                audit.rulesetVersion()
        );
    }
}
