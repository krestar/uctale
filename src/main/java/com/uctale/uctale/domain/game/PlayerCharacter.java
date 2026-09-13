package com.uctale.uctale.domain.game;

import java.util.Objects;

public record PlayerCharacter(
        String description,
        CharacterStats stats,
        CharacterVitals vitals
) {
    public PlayerCharacter {
        description = description == null ? "" : description.trim();
        Objects.requireNonNull(stats, "stats는 필수입니다.");
        Objects.requireNonNull(vitals, "vitals는 필수입니다.");
    }

    public PlayerCharacter(String description, CharacterStats stats) {
        this(description, stats, CharacterVitals.defaults());
    }

    public static PlayerCharacter initial(String characterSetting) {
        return new PlayerCharacter(characterSetting, CharacterStats.defaults(), CharacterVitals.defaults());
    }

    public PlayerCharacter withVitals(CharacterVitals nextVitals) {
        return new PlayerCharacter(description, stats, nextVitals);
    }
}
