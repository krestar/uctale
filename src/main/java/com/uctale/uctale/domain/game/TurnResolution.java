package com.uctale.uctale.domain.game;

import java.util.Objects;

public record TurnResolution(GameResult gameResult, StateTransition stateTransition) {

    public TurnResolution {
        Objects.requireNonNull(gameResult, "gameResult는 필수입니다.");
        Objects.requireNonNull(stateTransition, "stateTransition은 필수입니다.");
        if (gameResult.resolvedAction().sourceTurn() != stateTransition.previousState().turnNumber()) {
            throw new IllegalArgumentException("resolved action과 state transition의 source turn이 일치하지 않습니다.");
        }
    }

    public StateTransition attachNarrative(String storyText) {
        return stateTransition.attachNarrative(gameResult.resolvedAction(), storyText);
    }
}
