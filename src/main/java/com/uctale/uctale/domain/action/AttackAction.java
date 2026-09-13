package com.uctale.uctale.domain.action;

import java.util.Map;
import java.util.Objects;

public record AttackAction(String encounterId, String targetEnemyId) {
    public AttackAction {
        encounterId = requireText(encounterId, "encounterId");
        targetEnemyId = requireText(targetEnemyId, "targetEnemyId");
    }

    public static AttackAction from(PlayerAction action) {
        Objects.requireNonNull(action, "PlayerAction은 필수입니다.");
        if (action.type() != ActionType.COMBAT_ATTACK) {
            throw new IllegalArgumentException("COMBAT_ATTACK action이 필요합니다.");
        }
        Map<String, String> arguments = action.arguments();
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("AttackAction arguments가 올바르지 않습니다.");
        }
        try {
            return new AttackAction(arguments.get("encounterId"), arguments.get("targetEnemyId"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("AttackAction arguments가 올바르지 않습니다.", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value.trim();
    }
}
