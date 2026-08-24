package com.uctale.uctale.domain.game;

import java.util.Map;

public record PlayerCharacter(
        String description,
        Map<String, Integer> stats
) {
    public PlayerCharacter {
        description = description == null ? "" : description.trim();
        stats = stats == null ? Map.of() : Map.copyOf(stats);
    }

    public static PlayerCharacter initial(String characterSetting) {
        return new PlayerCharacter(characterSetting, Map.of());
    }
}
