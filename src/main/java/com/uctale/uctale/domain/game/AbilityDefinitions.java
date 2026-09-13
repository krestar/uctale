package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AbilityDefinitions {
    private static final Map<String, AbilityDefinition> DEFINITIONS;

    static {
        TreeMap<String, AbilityDefinition> definitions = new TreeMap<>();
        register(definitions, new AbilityDefinition(
                "arcane-bolt", 3, 2, AbilityTargetRule.ENEMY, AbilityEffectType.DAMAGE, 4, null));
        register(definitions, new AbilityDefinition(
                "second-wind", 2, 2, AbilityTargetRule.SELF, AbilityEffectType.HEAL, 4, null));
        register(definitions, new AbilityDefinition(
                "steady-focus", 2, 3, AbilityTargetRule.SELF, AbilityEffectType.STATUS, 0,
                new StatusEffect("focused", 1, 1, 2, StatusExpiryTrigger.END_OF_TURN, false)));
        DEFINITIONS = Collections.unmodifiableMap(new TreeMap<>(definitions));
    }

    private AbilityDefinitions() {}

    public static AbilityDefinition require(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("ability definitionId는 비어 있을 수 없습니다.");
        AbilityDefinition definition = DEFINITIONS.get(definitionId.trim());
        if (definition == null) throw new IllegalArgumentException("지원하지 않는 ability입니다: " + definitionId);
        return definition;
    }

    public static List<AbilityDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    private static void register(Map<String, AbilityDefinition> definitions, AbilityDefinition definition) {
        if (definitions.put(definition.definitionId(), definition) != null) {
            throw new IllegalStateException("중복 ability definitionId입니다: " + definition.definitionId());
        }
    }
}
