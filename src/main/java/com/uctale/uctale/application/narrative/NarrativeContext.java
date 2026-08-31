package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.CanonicalFact;
import com.uctale.uctale.domain.game.CharacterStats;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.TurnResolution;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NarrativeContext(
        String canonicalResultId,
        ResolvedAction resolvedAction,
        GameResult.Outcome outcome,
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
            "GameResult.stateChanges에 없는 HP, 능력치, 아이템, 레벨, 위치, 생사 변화를 확정하지 않는다.",
            "서버가 제공하지 않은 roll이나 판정 결과를 새로 만들지 않는다.",
            "state projection과 canonical facts를 수정하거나 충돌하는 사실을 확정하지 않는다."
    );

    public NarrativeContext {
        if (canonicalResultId == null || canonicalResultId.isBlank()) {
            throw new IllegalArgumentException("canonicalResultId는 필수입니다.");
        }
        Objects.requireNonNull(resolvedAction, "resolvedAction은 필수입니다.");
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        resultCanonicalFacts = resultCanonicalFacts == null ? List.of() : List.copyOf(resultCanonicalFacts);
        events = events == null ? List.of() : List.copyOf(events);
        stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        Objects.requireNonNull(state, "state projection은 필수입니다.");
        Objects.requireNonNull(memory, "memory projection은 필수입니다.");
        narrativeCues = narrativeCues == null ? List.of() : List.copyOf(narrativeCues);
        forbiddenCanonicalMutations = forbiddenCanonicalMutations == null
                ? CANONICAL_MUTATION_GUARDRAILS
                : List.copyOf(forbiddenCanonicalMutations);
    }

    public static NarrativeContext from(String canonicalResultId, TurnResolution resolution) {
        Objects.requireNonNull(resolution, "TurnResolution은 필수입니다.");
        GameResult result = resolution.gameResult();
        GameState canonicalNextState = resolution.stateTransition().nextState();
        return new NarrativeContext(
                canonicalResultId,
                ResolvedAction.from(result.resolvedAction()),
                result.outcome(),
                result.canonicalFacts(),
                result.events(),
                result.stateChanges(),
                StateProjection.from(canonicalNextState),
                MemoryProjection.from(canonicalNextState),
                result.narrativeCues(),
                CANONICAL_MUTATION_GUARDRAILS
        );
    }

    public String playerAction() {
        return resolvedAction.displayText();
    }

    public record ResolvedAction(
            int legacyChoiceId,
            ActionType type,
            int sourceTurn,
            Map<String, String> arguments,
            String displayText
    ) {
        public ResolvedAction {
            if (legacyChoiceId < 1 || sourceTurn < 1) {
                throw new IllegalArgumentException("resolved action 식별자가 올바르지 않습니다.");
            }
            Objects.requireNonNull(type, "action type은 필수입니다.");
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
            displayText = displayText == null ? "" : displayText;
        }

        private static ResolvedAction from(PlayerAction action) {
            return new ResolvedAction(
                    action.legacyChoiceId(),
                    action.type(),
                    action.sourceTurn(),
                    action.arguments(),
                    action.displayText()
            );
        }
    }

    public record StateProjection(
            int turnNumber,
            String worldPremise,
            String playerDescription,
            CharacterStats playerStats,
            Map<String, String> worldFlags
    ) {
        public StateProjection {
            if (turnNumber < 1) throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
            worldPremise = worldPremise == null ? "" : worldPremise;
            playerDescription = playerDescription == null ? "" : playerDescription;
            Objects.requireNonNull(playerStats, "playerStats는 필수입니다.");
            worldFlags = worldFlags == null ? Map.of() : Map.copyOf(worldFlags);
        }

        private static StateProjection from(GameState state) {
            return new StateProjection(
                    state.turnNumber(),
                    state.worldState().premise(),
                    state.playerCharacter().description(),
                    state.playerCharacter().stats(),
                    state.worldState().flags()
            );
        }
    }

    public record MemoryProjection(
            List<CanonicalFact> canonicalFacts,
            String rollingSummary,
            List<GameTurn> recentTurns
    ) {
        public MemoryProjection {
            canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
            rollingSummary = rollingSummary == null ? "" : rollingSummary;
            recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        }

        private static MemoryProjection from(GameState state) {
            return new MemoryProjection(
                    state.storyMemory().canonicalFacts(),
                    state.storyMemory().rollingSummary(),
                    state.storyMemory().recentTurns()
            );
        }
    }
}
