package com.uctale.uctale.domain.game;

import java.util.Map;

public record WorldState(
        String premise,
        Map<String, String> flags
) {
    public WorldState {
        premise = premise == null ? "" : premise.trim();
        flags = flags == null ? Map.of() : Map.copyOf(flags);
    }

    public static WorldState initial(String worldSetting) {
        return new WorldState(worldSetting, Map.of());
    }
}
