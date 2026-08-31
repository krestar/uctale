package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;

public final class TurnProcessor {

    private final ActionResolver actionResolver = new ActionResolver();

    public TurnResolution resolve(GameState state, PlayerAction action) {
        return actionResolver.resolve(state, action);
    }

    public StateTransition attachNarrative(TurnResolution resolution, String storyText) {
        if (resolution == null) {
            throw new IllegalArgumentException("TurnResolution은 필수입니다.");
        }
        return resolution.attachNarrative(storyText);
    }
}
