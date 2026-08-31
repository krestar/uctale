package com.uctale.uctale.domain.game;

public record PlayerCharacter(
        String description,
        CharacterStats stats
) {
    public PlayerCharacter {
        description = description == null ? "" : description.trim();
        stats = stats == null ? CharacterStats.defaults() : stats;
    }

    public static PlayerCharacter initial(String characterSetting) {
        return new PlayerCharacter(characterSetting, CharacterStats.defaults());
    }
}
