package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record CharacterVitals(
        ResourcePool hp,
        ResourcePool mp,
        Map<String, StatusEffect> statusEffects
) {
    public static final int DEFAULT_MAX_HP = 10;
    public static final int DEFAULT_MAX_MP = 10;

    public CharacterVitals {
        Objects.requireNonNull(hp, "hp는 필수입니다.");
        Objects.requireNonNull(mp, "mp는 필수입니다.");
        Objects.requireNonNull(statusEffects, "statusEffects는 필수입니다.");
        if (statusEffects.isEmpty()) {
            statusEffects = Map.of();
        } else {
            TreeMap<String, StatusEffect> copy = new TreeMap<>();
            for (Map.Entry<String, StatusEffect> entry : statusEffects.entrySet()) {
                String key = entry.getKey();
                StatusEffect effect = Objects.requireNonNull(entry.getValue(), "status effect는 null일 수 없습니다.");
                if (key == null || !key.equals(effect.definitionId())) {
                    throw new IllegalArgumentException("status effect key와 definitionId가 일치해야 합니다.");
                }
                copy.put(key, effect);
            }
            statusEffects = Collections.unmodifiableMap(copy);
        }
    }

    public static CharacterVitals defaults() {
        return new CharacterVitals(
                ResourcePool.full(DEFAULT_MAX_HP),
                ResourcePool.full(DEFAULT_MAX_MP),
                Map.of()
        );
    }

    public CharacterVitals withHp(ResourcePool nextHp) {
        return new CharacterVitals(nextHp, mp, statusEffects);
    }

    public CharacterVitals withMp(ResourcePool nextMp) {
        return new CharacterVitals(hp, nextMp, statusEffects);
    }

    public CharacterVitals withStatusEffects(Map<String, StatusEffect> nextStatusEffects) {
        return new CharacterVitals(hp, mp, nextStatusEffects);
    }

    public StatusEffect requireStatus(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("status effect definitionId는 비어 있을 수 없습니다.");
        }
        StatusEffect effect = statusEffects.get(definitionId);
        if (effect == null) {
            throw new IllegalArgumentException("존재하지 않는 status effect입니다: " + definitionId);
        }
        return effect;
    }

    public boolean defeated() {
        return hp.current() == 0;
    }

    public boolean incapacitated() {
        return defeated() || statusEffects.values().stream().anyMatch(StatusEffect::incapacitating);
    }
}
