package com.uctale.uctale.domain.game;

public record GameTurn(
        int turnNumber,
        String playerAction,
        String storyText
) {
    public GameTurn {
        if (turnNumber < 1) {
            throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
        }
        playerAction = playerAction == null ? "" : playerAction.trim();
        storyText = storyText == null ? "" : storyText.trim();
    }

    public static GameTurn opening(String storyText) {
        return new GameTurn(1, "", storyText);
    }
}
