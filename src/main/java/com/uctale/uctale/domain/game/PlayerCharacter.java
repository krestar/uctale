package com.uctale.uctale.domain.game;

import java.util.Objects;

public record PlayerCharacter(
        String description,
        CharacterStats stats
) {
    public PlayerCharacter {
        description = description == null ? "" : description.trim();
        Objects.requireNonNull(stats, "stats는 필수입니다.");
    }

    public static PlayerCharacter initial(String characterSetting) {
        return new PlayerCharacter(characterSetting, CharacterStats.defaults());
    }
}
