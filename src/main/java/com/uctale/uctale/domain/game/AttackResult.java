package com.uctale.uctale.domain.game;

import java.util.Objects;

public record AttackResult(
        String encounterId,
        String targetEnemyId,
        StatType statType,
        int attackRoll,
        int statModifier,
        int equipmentAttackModifier,
        int defenseScore,
        int attackTotal,
        AttackOutcome outcome,
        int damageRoll,
        int damageStatModifier,
        int equipmentDamageModifier,
        int damageTotal,
        int damageMitigation,
        int damageAfterMitigation,
        int finalDamage,
        int targetHpBefore,
        int targetHpAfter,
        boolean targetDefeated,
        int rulesetVersion
) {
    public AttackResult {
        encounterId = requireText(encounterId, "encounterId");
        targetEnemyId = requireText(targetEnemyId, "targetEnemyId");
        Objects.requireNonNull(statType, "statType은 필수입니다.");
        Objects.requireNonNull(outcome, "attack outcome은 필수입니다.");
        if (statType != AttackRules.ATTACK_STAT) throw new IllegalArgumentException("현재 attack ruleset은 MIGHT만 사용합니다.");
        if (attackRoll < AttackRules.ATTACK_ROLL_MIN || attackRoll > AttackRules.ATTACK_ROLL_MAX) throw new IllegalArgumentException("attack roll이 허용 범위를 벗어났습니다.");
        if (defenseScore < EnemyCombatProfile.MIN_DEFENSE_SCORE || defenseScore > EnemyCombatProfile.MAX_DEFENSE_SCORE) throw new IllegalArgumentException("defenseScore가 허용 범위를 벗어났습니다.");
        long expectedAttackTotal = (long) attackRoll + statModifier + equipmentAttackModifier;
        if (expectedAttackTotal < Integer.MIN_VALUE || expectedAttackTotal > Integer.MAX_VALUE || attackTotal != (int) expectedAttackTotal) throw new IllegalArgumentException("attack total이 raw roll/modifier와 일치하지 않습니다.");
        AttackOutcome expectedOutcome = attackTotal >= defenseScore ? AttackOutcome.HIT : AttackOutcome.MISS;
        if (outcome != expectedOutcome) throw new IllegalArgumentException("attack outcome이 attack total/defense와 일치하지 않습니다.");
        if (damageMitigation < 0 || damageMitigation > EnemyCombatProfile.MAX_DAMAGE_REDUCTION) throw new IllegalArgumentException("damage mitigation이 허용 범위를 벗어났습니다.");
        if (targetHpBefore < 1 || targetHpAfter < 0) throw new IllegalArgumentException("target HP audit 값이 올바르지 않습니다.");
        if (rulesetVersion != AttackRules.RULESET_VERSION) throw new IllegalArgumentException("지원하지 않는 attack rulesetVersion입니다.");

        if (outcome == AttackOutcome.MISS) {
            if (damageRoll != 0 || damageTotal != 0 || damageAfterMitigation != 0 || finalDamage != 0 || targetHpAfter != targetHpBefore || targetDefeated) {
                throw new IllegalArgumentException("MISS 결과에 피해나 HP 변화를 기록할 수 없습니다.");
            }
        } else {
            if (damageRoll < AttackRules.DAMAGE_ROLL_MIN || damageRoll > AttackRules.DAMAGE_ROLL_MAX) throw new IllegalArgumentException("damage roll이 허용 범위를 벗어났습니다.");
            long rawDamageTotal = (long) damageRoll + damageStatModifier + equipmentDamageModifier;
            if (rawDamageTotal > Integer.MAX_VALUE) throw new IllegalArgumentException("damage total이 지원 범위를 벗어났습니다.");
            int expectedDamageTotal = (int) Math.max(0L, rawDamageTotal);
            if (damageTotal != expectedDamageTotal) throw new IllegalArgumentException("damage total이 raw roll/modifier와 일치하지 않습니다.");
            int expectedAfterMitigation = Math.max(0, damageTotal - damageMitigation);
            if (damageAfterMitigation != expectedAfterMitigation) throw new IllegalArgumentException("mitigation 이후 damage가 계산식과 일치하지 않습니다.");
            int expectedFinalDamage = Math.min(targetHpBefore, damageAfterMitigation);
            if (finalDamage != expectedFinalDamage || targetHpAfter != targetHpBefore - finalDamage) throw new IllegalArgumentException("final damage와 target HP가 일치하지 않습니다.");
            if (targetDefeated != (targetHpAfter == 0)) throw new IllegalArgumentException("target defeated 상태가 HP와 일치하지 않습니다.");
        }
    }

    public boolean hit() { return outcome == AttackOutcome.HIT; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        return value.trim();
    }
}
