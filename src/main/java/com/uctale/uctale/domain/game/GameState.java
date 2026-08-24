package com.uctale.uctale.domain.game;

public record GameState(
        int turnNumber,
        PlayerCharacter playerCharacter,
        WorldState worldState,
        StoryMemory storyMemory
) {
    public GameState {
        if (turnNumber < 1) {
            throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
        }
        if (playerCharacter == null || worldState == null || storyMemory == null) {
            throw new IllegalArgumentException("GameState 구성 요소는 null일 수 없습니다.");
        }
    }

    public static GameState initial(String worldSetting, String characterSetting, String openingStory) {
        return new GameState(
                1,
                PlayerCharacter.initial(characterSetting),
                WorldState.initial(worldSetting),
                StoryMemory.initial(worldSetting, characterSetting, openingStory)
        );
    }

    public GameState advance(String playerAction, String storyText) {
        int nextTurn = turnNumber + 1;
        return new GameState(
                nextTurn,
                playerCharacter,
                worldState,
                storyMemory.append(new GameTurn(nextTurn, playerAction, storyText))
        );
    }
}
