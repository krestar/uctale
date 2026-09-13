package com.uctale.uctale.domain.game;

import java.util.Objects;

public record AbilityResult(
        String definitionId,
        AbilityTargetRule targetRule,
        String targetId,
        int mpCost,
        int mpBefore,
        int mpAfter,
        int cooldownTurns,
        AbilityEffectType effectType,
        int appliedAmount,
        String statusDefinitionId,
        Integer targetHpBefore,
        Integer targetHpAfter
) {
    public AbilityResult {
        definitionId = requireText(definitionId, "ability definitionId");
        targetId = requireText(targetId, "ability targetId");
        Objects.requireNonNull(targetRule, "ability targetRule은 필수입니다.");
        Objects.requireNonNull(effectType, "ability effectType은 필수입니다.");
        if (mpCost < 0 || mpBefore < 0 || mpAfter < 0 || mpAfter != mpBefore - mpCost) {
            throw new IllegalArgumentException("ability MP 결과가 올바르지 않습니다.");
        }
        if (cooldownTurns < 1 || appliedAmount < 1) throw new IllegalArgumentException("ability 결과 수치가 올바르지 않습니다.");
        if (effectType == AbilityEffectType.STATUS) {
            statusDefinitionId = requireText(statusDefinitionId, "statusDefinitionId");
            if (targetHpBefore != null || targetHpAfter != null) throw new IllegalArgumentException("STATUS ability에는 target HP 결과를 기록할 수 없습니다.");
        } else {
            if (statusDefinitionId != null) throw new IllegalArgumentException("DAMAGE/HEAL ability에는 statusDefinitionId를 기록할 수 없습니다.");
            if (targetHpBefore == null || targetHpAfter == null || targetHpBefore < 0 || targetHpAfter < 0) {
                throw new IllegalArgumentException("DAMAGE/HEAL ability에는 HP 결과가 필요합니다.");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "는 비어 있을 수 없습니다.");
        return value.trim();
    }
}
