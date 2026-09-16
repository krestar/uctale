package com.uctale.uctale.domain.game;

import java.util.List;
import java.util.Set;

public final class StoryMemoryFactPolicy {

    private static final Set<String> EXACT_STATE_OWNED_KEYS = Set.of(
            "world.premise",
            "player.description"
    );

    private static final List<String> STATE_OWNED_PREFIXES = List.of(
            "player.stats.",
            "player.vitals.",
            "inventory.",
            "equipment.",
            "quest.",
            "relationship.",
            "npc.",
            "world.flag.",
            "world.event.",
            "combat.",
            "ability."
    );

    private StoryMemoryFactPolicy() {
    }

    public static boolean isStateOwned(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim();
        if (EXACT_STATE_OWNED_KEYS.contains(normalized)) {
            return true;
        }
        return STATE_OWNED_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
