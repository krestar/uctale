package com.uctale.uctale.domain.game;

public record GameState(
        int turnNumber,
        PlayerCharacter playerCharacter,
        WorldState worldState,
        StoryMemory storyMemory,
        Inventory inventory
) {
    public GameState {
        if (turnNumber < 1) {
            throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
        }
        if (playerCharacter == null || worldState == null || storyMemory == null || inventory == null) {
            throw new IllegalArgumentException("GameState 구성 요소는 null일 수 없습니다.");
        }
    }

    public GameState(
            int turnNumber,
            PlayerCharacter playerCharacter,
            WorldState worldState,
            StoryMemory storyMemory
    ) {
        this(turnNumber, playerCharacter, worldState, storyMemory, Inventory.empty());
    }

    public static GameState initial(String worldSetting, String characterSetting, String openingStory) {
        return new GameState(
                1,
                PlayerCharacter.initial(characterSetting),
                WorldState.initial(worldSetting),
                StoryMemory.initial(worldSetting, characterSetting, openingStory),
                Inventory.empty()
        );
    }

    public GameState withInventory(Inventory nextInventory) {
        if (nextInventory == null) {
            throw new IllegalArgumentException("inventory는 null일 수 없습니다.");
        }
        return new GameState(turnNumber, playerCharacter, worldState, storyMemory, nextInventory);
    }

    public GameState advanceTurn() {
        return new GameState(
                turnNumber + 1,
                playerCharacter,
                worldState,
                storyMemory,
                inventory
        );
    }

    public GameState recordNarrativeTurn(String playerAction, String storyText) {
        if (!storyMemory.recentTurns().isEmpty()
                && storyMemory.recentTurns().getLast().turnNumber() >= turnNumber) {
            throw new IllegalStateException("현재 turn의 narrative가 이미 기록되어 있습니다.");
        }
        return new GameState(
                turnNumber,
                playerCharacter,
                worldState,
                storyMemory.append(new GameTurn(turnNumber, playerAction, storyText)),
                inventory
        );
    }

    public GameState advance(String playerAction, String storyText) {
        return advanceTurn().recordNarrativeTurn(playerAction, storyText);
    }
}
