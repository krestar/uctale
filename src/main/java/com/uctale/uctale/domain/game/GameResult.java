package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.PlayerAction;

import java.util.List;
import java.util.Objects;

public record GameResult(
        PlayerAction resolvedAction,
        Outcome outcome,
        List<CanonicalFact> canonicalFacts,
        List<GameEvent> events,
        List<StateChange> stateChanges,
        List<String> narrativeCues
) {
    public GameResult {
        Objects.requireNonNull(resolvedAction, "resolvedAction은 필수입니다.");
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
        events = events == null ? List.of() : List.copyOf(events);
        stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        narrativeCues = narrativeCues == null ? List.of() : List.copyOf(narrativeCues);
    }

    public enum Outcome {
        RESOLVED
    }

    public enum GameEvent {
        ACTION_RESOLVED
    }

    public sealed interface StateChange permits TurnAdvanced {}

    public record TurnAdvanced(int previousTurn, int nextTurn) implements StateChange {
        public TurnAdvanced {
            if (previousTurn < 1 || nextTurn != previousTurn + 1) {
                throw new IllegalArgumentException("turn state change가 올바르지 않습니다.");
            }
        }
    }
}
