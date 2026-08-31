package com.uctale.uctale.application.game;

import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;

public record GameTurnCommit(
        int expectedTurn,
        int inputChoiceId,
        String inputChoiceText,
        StateTransition stateTransition,
        String storyText,
        String choicesJson,
        ImageAssetService.AssetReference imageAsset
) {
    public GameTurnCommit {
        if (expectedTurn < 1) {
            throw new IllegalArgumentException("expectedTurn은 1 이상이어야 합니다.");
        }
        if (inputChoiceId < 1) {
            throw new IllegalArgumentException("inputChoiceId는 1 이상이어야 합니다.");
        }
        if (inputChoiceText == null || inputChoiceText.isBlank()) {
            throw new IllegalArgumentException("inputChoiceText는 비어 있을 수 없습니다.");
        }
        if (stateTransition == null) {
            throw new IllegalArgumentException("stateTransition은 null일 수 없습니다.");
        }
        if (stateTransition.previousState().turnNumber() != expectedTurn) {
            throw new IllegalArgumentException("previousState turn과 expectedTurn이 일치해야 합니다.");
        }
        if (stateTransition.nextState().turnNumber() != expectedTurn + 1) {
            throw new IllegalArgumentException("nextState는 expectedTurn의 다음 turn이어야 합니다.");
        }
        if (storyText == null || storyText.isBlank()) {
            throw new IllegalArgumentException("storyText는 비어 있을 수 없습니다.");
        }
        if (choicesJson == null) {
            throw new IllegalArgumentException("choicesJson은 null일 수 없습니다.");
        }
    }

    public GameTurnCommit(
            int expectedTurn,
            int inputChoiceId,
            String inputChoiceText,
            GameState previousState,
            GameState nextState,
            String storyText,
            String choicesJson,
            ImageAssetService.AssetReference imageAsset
    ) {
        this(expectedTurn, inputChoiceId, inputChoiceText, new StateTransition(previousState, nextState),
                storyText, choicesJson, imageAsset);
    }

    public GameState previousState() {
        return stateTransition.previousState();
    }

    public GameState nextState() {
        return stateTransition.nextState();
    }

    public int previousStateVersion() {
        return previousState().turnNumber();
    }

    public int nextStateVersion() {
        return nextState().turnNumber();
    }
}
