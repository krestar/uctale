package com.uctale.uctale.domain.game;

import java.util.Objects;

public record AbilityDefinition(
        String definitionId,
        int mpCost,
        int cooldownTurns,
        AbilityTargetRule targetRule,
        AbilityEffectType effectType,
        int effectAmount,
        StatusEffect statusEffect
) {
    public AbilityDefinition {
        definitionId = definitionId == null ? "" : definitionId.trim();
        if (definitionId.isBlank()) throw new IllegalArgumentException("ability definitionId는 비어 있을 수 없습니다.");
        if (mpCost < 0) throw new IllegalArgumentException("ability MP cost는 0 이상이어야 합니다.");
        if (cooldownTurns < 1) throw new IllegalArgumentException("ability cooldown은 1턴 이상이어야 합니다.");
        Objects.requireNonNull(targetRule, "ability targetRule은 필수입니다.");
        Objects.requireNonNull(effectType, "ability effectType은 필수입니다.");
        if (effectType == AbilityEffectType.STATUS) {
            if (targetRule != AbilityTargetRule.SELF || statusEffect == null || effectAmount != 0) {
                throw new IllegalArgumentException("STATUS ability 정의가 올바르지 않습니다.");
            }
        } else {
            if (statusEffect != null || effectAmount < 1) throw new IllegalArgumentException("ability effect amount가 올바르지 않습니다.");
            if (effectType == AbilityEffectType.DAMAGE && targetRule != AbilityTargetRule.ENEMY) {
                throw new IllegalArgumentException("DAMAGE ability는 ENEMY target이어야 합니다.");
            }
            if (effectType == AbilityEffectType.HEAL && targetRule != AbilityTargetRule.SELF) {
                throw new IllegalArgumentException("HEAL ability는 SELF target이어야 합니다.");
            }
        }
    }
}
