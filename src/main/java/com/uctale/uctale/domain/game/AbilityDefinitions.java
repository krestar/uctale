package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.TreeMap;

public final class AbilityDefinitions {
    private static final Map<String, AbilityDefinition> DEFINITIONS;
    private static final String DYNAMIC_PREFIX = "ability.v2.";
    private static final Pattern SUBJECT = Pattern.compile("[a-z][a-z0-9-]{0,31}");

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
        if (definition == null) definition = dynamic(definitionId.trim()).orElse(null);
        if (definition == null) throw new IllegalArgumentException("지원하지 않는 ability입니다: " + definitionId);
        return definition;
    }

    public static Optional<AbilityDefinition> dynamicForEnemy(String enemyId) {
        if (enemyId == null || !enemyId.startsWith("enemy:")) return Optional.empty();
        return dynamic(DYNAMIC_PREFIX + enemyId.substring("enemy:".length()));
    }

    private static Optional<AbilityDefinition> dynamic(String definitionId) {
        if (!definitionId.startsWith(DYNAMIC_PREFIX)) return Optional.empty();
        String subject = definitionId.substring(DYNAMIC_PREFIX.length());
        if (!SUBJECT.matcher(subject).matches()) return Optional.empty();
        // All numeric values are fixed by ruleset v2; the provider only supplied the validated subject.
        return Optional.of(new AbilityDefinition(definitionId, 2, 2,
                AbilityTargetRule.ENEMY, AbilityEffectType.DAMAGE, 3, null));
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
