package com.uctale.uctale.domain.action;

import java.util.Map;
import java.util.Objects;

public record UseAbilityAction(String encounterId, String abilityDefinitionId, String targetId) {
    public UseAbilityAction {
        encounterId = requireText(encounterId, "encounterId");
        abilityDefinitionId = requireText(abilityDefinitionId, "abilityDefinitionId");
        targetId = requireText(targetId, "targetId");
    }

    public static UseAbilityAction from(PlayerAction action) {
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        if (action.type() != ActionType.COMBAT_ABILITY) {
            throw new IllegalArgumentException("COMBAT_ABILITY action이 필요합니다.");
        }
        Map<String, String> arguments = action.arguments();
        if (arguments.size() != 3) {
            throw new IllegalArgumentException("UseAbilityAction arguments가 올바르지 않습니다.");
        }
        try {
            return new UseAbilityAction(
                    arguments.get("encounterId"),
                    arguments.get("abilityDefinitionId"),
                    arguments.get("targetId")
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("UseAbilityAction arguments가 올바르지 않습니다.", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value.trim();
    }
}
