package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record AbilityState(Map<String, Integer> cooldowns) {
    public AbilityState {
        if (cooldowns == null) throw new IllegalArgumentException("ability cooldowns는 null일 수 없습니다.");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            String definitionId = entry.getKey();
            Integer remaining = entry.getValue();
            if (definitionId == null || definitionId.isBlank() || remaining == null || remaining < 1) {
                throw new IllegalArgumentException("ability cooldown entry가 올바르지 않습니다.");
            }
            copy.put(definitionId, remaining);
        }
        cooldowns = copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }

    public static AbilityState empty() {
        return new AbilityState(Map.of());
    }

    public int remainingCooldown(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("ability definitionId는 비어 있을 수 없습니다.");
        return cooldowns.getOrDefault(definitionId, 0);
    }

    AbilityState withCooldown(String definitionId, int remainingTurns) {
        TreeMap<String, Integer> next = new TreeMap<>(cooldowns);
        if (remainingTurns == 0) next.remove(definitionId);
        else {
            if (remainingTurns < 0) throw new IllegalArgumentException("ability cooldown은 음수일 수 없습니다.");
            next.put(definitionId, remainingTurns);
        }
        return new AbilityState(next);
    }
}
