package com.uctale.uctale.application.game;

import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StateTransition;

public record GameTurnCommit(
        int expectedTurn,
        int inputChoiceId,
        String inputChoiceText,
        StateTransition stateTransition,
        String storyText,
        String choicesJson,
        String canonicalResultId,
        String generatedStoryId,
        SkillCheckResult skillCheckResult,
        ImageAssetService.AssetReference imageAsset
) {
    private static final int MAX_LINK_ID_LENGTH = 128;

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
        if ((canonicalResultId == null) != (generatedStoryId == null)) {
            throw new IllegalArgumentException("canonicalResultId와 generatedStoryId는 함께 기록해야 합니다.");
        }
        validateLinkId("canonicalResultId", canonicalResultId);
        validateLinkId("generatedStoryId", generatedStoryId);
    }

    public GameTurnCommit(
            int expectedTurn,
            int inputChoiceId,
            String inputChoiceText,
            StateTransition stateTransition,
            String storyText,
            String choicesJson,
            String canonicalResultId,
            String generatedStoryId,
            ImageAssetService.AssetReference imageAsset
    ) {
        this(expectedTurn, inputChoiceId, inputChoiceText, stateTransition, storyText, choicesJson,
                canonicalResultId, generatedStoryId, null, imageAsset);
    }

    public GameTurnCommit(
            int expectedTurn,
            int inputChoiceId,
            String inputChoiceText,
            StateTransition stateTransition,
            String storyText,
            String choicesJson,
            ImageAssetService.AssetReference imageAsset
    ) {
        this(expectedTurn, inputChoiceId, inputChoiceText, stateTransition, storyText, choicesJson,
                null, null, null, imageAsset);
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
                storyText, choicesJson, null, null, null, imageAsset);
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

    public boolean hasNarrativeLink() {
        return canonicalResultId != null;
    }

    public boolean hasSkillCheck() {
        return skillCheckResult != null;
    }

    private static void validateLinkId(String name, String value) {
        if (value == null) return;
        if (value.isBlank() || value.length() > MAX_LINK_ID_LENGTH) {
            throw new IllegalArgumentException(name + "가 올바르지 않습니다.");
        }
    }
}
