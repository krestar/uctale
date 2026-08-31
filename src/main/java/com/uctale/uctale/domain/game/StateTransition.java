package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.PlayerAction;

import java.util.Objects;

public record StateTransition(GameState previousState, GameState nextState) {

    public StateTransition {
        Objects.requireNonNull(previousState, "previousState는 필수입니다.");
        Objects.requireNonNull(nextState, "nextState는 필수입니다.");
        if (nextState.turnNumber() != previousState.turnNumber() + 1) {
            throw new IllegalArgumentException("nextState는 previousState의 다음 turn이어야 합니다.");
        }
    }

    public StateTransition attachNarrative(PlayerAction resolvedAction, String storyText) {
        Objects.requireNonNull(resolvedAction, "resolvedAction은 필수입니다.");
        if (storyText == null || storyText.isBlank()) {
            throw new IllegalArgumentException("storyText는 비어 있을 수 없습니다.");
        }
        if (resolvedAction.sourceTurn() != previousState.turnNumber()) {
            throw new IllegalArgumentException("resolvedAction source turn이 transition과 일치하지 않습니다.");
        }
        return new StateTransition(previousState, nextState.recordNarrativeTurn(resolvedAction.displayText(), storyText));
    }
}
