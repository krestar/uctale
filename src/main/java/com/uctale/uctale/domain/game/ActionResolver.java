package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ActionResolver {

    public TurnResolution resolve(GameState state, PlayerAction action) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        if (action.sourceTurn() != state.turnNumber()) {
            throw new IllegalArgumentException("PlayerAction source turn이 현재 GameState와 일치하지 않습니다.");
        }
        if (action.type() != ActionType.NARRATIVE_CHOICE) {
            throw new IllegalArgumentException("지원하지 않는 action type입니다: " + action.type());
        }
        validateNarrativeChoice(action);

        GameState nextState = state.advanceTurn();
        GameResult result = new GameResult(
                action,
                GameResult.Outcome.RESOLVED,
                List.of(),
                List.of(GameResult.GameEvent.ACTION_RESOLVED),
                List.of(new GameResult.TurnAdvanced(state.turnNumber(), nextState.turnNumber())),
                List.of(action.displayText())
        );
        return new TurnResolution(result, new StateTransition(state, nextState));
    }

    private void validateNarrativeChoice(PlayerAction action) {
        Map<String, String> arguments = action.arguments();
        String choiceId = arguments.get("choiceId");
        if (arguments.size() != 1 || !Integer.toString(action.legacyChoiceId()).equals(choiceId)) {
            throw new IllegalArgumentException("NARRATIVE_CHOICE arguments가 action과 일치하지 않습니다.");
        }
    }
}
