package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.CanonicalFact;
import com.uctale.uctale.domain.game.CharacterStats;
import com.uctale.uctale.domain.game.CharacterVitals;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.CombatEncounterStatus;
import com.uctale.uctale.domain.game.EnemyState;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.SkillCheckOutcome;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StatType;
import com.uctale.uctale.domain.game.TurnResolution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record NarrativeContext(
        String canonicalResultId,
        ResolvedAction resolvedAction,
        GameResult.Outcome outcome,
        SkillCheckProjection skillCheck,
        List<CanonicalFact> resultCanonicalFacts,
        List<GameResult.GameEvent> events,
        List<GameResult.StateChange> stateChanges,
        StateProjection state,
        MemoryProjection memory,
        List<String> narrativeCues,
        List<String> forbiddenCanonicalMutations
) {
    public static final List<String> CANONICAL_MUTATION_GUARDRAILS = List.of(
            "GameResult.outcome과 서버가 확정한 성공/실패를 변경하거나 다시 판정하지 않는다.",
            "GameResult.stateChanges에 없는 HP, MP, status, ability cooldown, 능력치, 아이템, 레벨, 위치, 생사 변화를 확정하지 않는다.",
            "combat projection/stateChanges에 없는 enemy 생성·제거·사망·부활·encounter lifecycle 변화를 확정하지 않는다.",
            "ability stateChanges에 기록된 비용·효과·target·cooldown을 변경하거나 다시 판정하지 않는다.",
            "서버가 제공하지 않은 roll이나 판정 결과를 새로 만들지 않는다.",
            "state projection과 canonical facts를 수정하거나 충돌하는 사실을 확정하지 않는다."
    );

    public NarrativeContext {
        if (canonicalResultId == null || canonicalResultId.isBlank()) throw new IllegalArgumentException("canonicalResultId는 필수입니다.");
        Objects.requireNonNull(resolvedAction, "resolvedAction은 필수입니다.");
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        resultCanonicalFacts = resultCanonicalFacts == null ? List.of() : List.copyOf(resultCanonicalFacts);
        events = events == null ? List.of() : List.copyOf(events);
        stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        Objects.requireNonNull(state, "state projection은 필수입니다.");
        Objects.requireNonNull(memory, "memory projection은 필수입니다.");
        narrativeCues = narrativeCues == null ? List.of() : List.copyOf(narrativeCues);
        forbiddenCanonicalMutations = forbiddenCanonicalMutations == null ? CANONICAL_MUTATION_GUARDRAILS : List.copyOf(forbiddenCanonicalMutations);
    }

    public static NarrativeContext from(String canonicalResultId, TurnResolution resolution) {
        Objects.requireNonNull(resolution, "TurnResolution은 필수입니다.");
        GameResult result = resolution.gameResult();
        GameState canonicalNextState = resolution.stateTransition().nextState();
        return new NarrativeContext(canonicalResultId, ResolvedAction.from(result.resolvedAction()), result.outcome(),
                SkillCheckProjection.from(result.skillCheckResult()), result.canonicalFacts(), result.events(),
                result.stateChanges(), StateProjection.from(canonicalNextState), MemoryProjection.from(canonicalNextState),
                result.narrativeCues(), CANONICAL_MUTATION_GUARDRAILS);
    }

    public String playerAction() { return resolvedAction.displayText(); }

    public record ResolvedAction(int legacyChoiceId, ActionType type, int sourceTurn,
                                 Map<String, String> arguments, String displayText) {
        public ResolvedAction {
            if (legacyChoiceId < 1 || sourceTurn < 1) throw new IllegalArgumentException("resolved action 식별자가 올바르지 않습니다.");
            Objects.requireNonNull(type, "action type은 필수입니다.");
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
            displayText = displayText == null ? "" : displayText;
        }
        private static ResolvedAction from(PlayerAction action) {
            return new ResolvedAction(action.legacyChoiceId(), action.type(), action.sourceTurn(), action.arguments(), action.displayText());
        }
    }

    public record SkillCheckProjection(StatType statType, int rawRoll, int statModifier,
            int situationalModifier, int dc, int total, SkillCheckOutcome outcome, int rulesetVersion) {
        private static SkillCheckProjection from(SkillCheckResult result) {
            if (result == null) return null;
            return new SkillCheckProjection(result.statType(), result.rawRoll(), result.statModifier(),
                    result.situationalModifier(), result.dc(), result.total(), result.outcome(), result.rulesetVersion());
        }
    }

    public record StateProjection(
            int turnNumber,
            String worldPremise,
            String playerDescription,
            CharacterStats playerStats,
            CharacterVitals playerVitals,
            boolean defeated,
            boolean incapacitated,
            Map<String, String> worldFlags,
            Map<String, Integer> abilityCooldowns,
            CombatProjection combat
    ) {
        public StateProjection {
            if (turnNumber < 1) throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
            worldPremise = worldPremise == null ? "" : worldPremise;
            playerDescription = playerDescription == null ? "" : playerDescription;
            Objects.requireNonNull(playerStats, "playerStats는 필수입니다.");
            Objects.requireNonNull(playerVitals, "playerVitals는 필수입니다.");
            if (defeated != playerVitals.defeated() || incapacitated != playerVitals.incapacitated()) throw new IllegalArgumentException("vitals 파생 상태가 canonical 값과 일치해야 합니다.");
            worldFlags = worldFlags == null ? Map.of() : Map.copyOf(worldFlags);
            abilityCooldowns = abilityCooldowns == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(abilityCooldowns));
        }

        private static StateProjection from(GameState state) {
            CharacterVitals vitals = state.playerCharacter().vitals();
            return new StateProjection(state.turnNumber(), state.worldState().premise(), state.playerCharacter().description(),
                    state.playerCharacter().stats(), vitals, vitals.defeated(), vitals.incapacitated(),
                    state.worldState().flags(), state.abilityState().cooldowns(), CombatProjection.from(state.combatEncounter()));
        }
    }

    public record CombatProjection(String encounterId, CombatEncounterStatus status, Map<String, EnemyProjection> enemies,
                                   List<String> turnOrder, String currentActorId) {
        public CombatProjection {
            if (encounterId == null || encounterId.isBlank()) throw new IllegalArgumentException("encounterId는 필수입니다.");
            Objects.requireNonNull(status, "combat status는 필수입니다.");
            enemies = enemies == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(enemies));
            turnOrder = turnOrder == null ? List.of() : List.copyOf(turnOrder);
        }
        private static CombatProjection from(CombatEncounter encounter) {
            if (encounter == null) return null;
            TreeMap<String, EnemyProjection> enemies = new TreeMap<>();
            encounter.enemies().forEach((id, enemy) -> enemies.put(id, EnemyProjection.from(enemy)));
            return new CombatProjection(encounter.encounterId(), encounter.status(), enemies, encounter.turnOrder(), encounter.currentActorId());
        }
    }

    public record EnemyProjection(String enemyId, String displayName, CharacterVitals vitals,
                                  boolean defeated, boolean incapacitated) {
        public EnemyProjection {
            if (enemyId == null || enemyId.isBlank() || displayName == null || displayName.isBlank()) throw new IllegalArgumentException("enemy projection 식별자는 필수입니다.");
            Objects.requireNonNull(vitals, "enemy vitals는 필수입니다.");
            if (defeated != vitals.defeated() || incapacitated != vitals.incapacitated()) throw new IllegalArgumentException("enemy vitals 파생 상태가 canonical 값과 일치해야 합니다.");
        }
        private static EnemyProjection from(EnemyState enemy) {
            return new EnemyProjection(enemy.enemyId(), enemy.displayName(), enemy.vitals(), enemy.defeated(), enemy.incapacitated());
        }
    }

    public record MemoryProjection(List<CanonicalFact> canonicalFacts, String rollingSummary, List<GameTurn> recentTurns) {
        public MemoryProjection {
            canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
            rollingSummary = rollingSummary == null ? "" : rollingSummary;
            recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        }
        private static MemoryProjection from(GameState state) {
            return new MemoryProjection(state.storyMemory().canonicalFacts(), state.storyMemory().rollingSummary(), state.storyMemory().recentTurns());
        }
    }
}
