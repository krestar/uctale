package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.AttackAction;

import java.util.Objects;

public final class AttackRules {
    public static final int RULESET_VERSION = 1;
    public static final StatType ATTACK_STAT = StatType.MIGHT;
    public static final int ATTACK_ROLL_MIN = 1;
    public static final int ATTACK_ROLL_MAX = 20;
    public static final int DAMAGE_ROLL_MIN = 1;
    public static final int DAMAGE_ROLL_MAX = 6;

    public AttackResult resolve(GameState state, AttackAction action, RandomSource randomSource) {
        Objects.requireNonNull(randomSource, "RandomSource는 필수입니다.");
        Inputs inputs = inputs(state, action);
        int attackRoll = randomSource.nextIntInclusive(ATTACK_ROLL_MIN, ATTACK_ROLL_MAX);
        int attackTotal = exactInt((long) attackRoll + inputs.statModifier() + inputs.equipmentAttackModifier(), "attack total");
        AttackOutcome outcome = attackTotal >= inputs.enemy().combatProfile().defenseScore() ? AttackOutcome.HIT : AttackOutcome.MISS;
        int damageRoll = outcome == AttackOutcome.HIT ? randomSource.nextIntInclusive(DAMAGE_ROLL_MIN, DAMAGE_ROLL_MAX) : 0;
        int damageTotal = outcome == AttackOutcome.HIT
                ? exactInt(Math.max(0L, (long) damageRoll + inputs.statModifier() + inputs.equipmentDamageModifier()), "damage total")
                : 0;
        int damageAfterMitigation = outcome == AttackOutcome.HIT ? Math.max(0, damageTotal - inputs.enemy().combatProfile().damageReduction()) : 0;
        int hpBefore = inputs.enemy().vitals().hp().current();
        int finalDamage = Math.min(hpBefore, damageAfterMitigation);
        int hpAfter = hpBefore - finalDamage;

        return new AttackResult(action.encounterId(), action.targetEnemyId(), ATTACK_STAT,
                attackRoll, inputs.statModifier(), inputs.equipmentAttackModifier(),
                inputs.enemy().combatProfile().defenseScore(), attackTotal, outcome,
                damageRoll, inputs.statModifier(), inputs.equipmentDamageModifier(), damageTotal,
                inputs.enemy().combatProfile().damageReduction(), damageAfterMitigation, finalDamage,
                hpBefore, hpAfter, hpAfter == 0, RULESET_VERSION);
    }

    public void validateStored(GameState state, AttackAction action, AttackResult result) {
        Objects.requireNonNull(result, "AttackResult는 필수입니다.");
        Inputs inputs = inputs(state, action);
        if (!action.encounterId().equals(result.encounterId()) || !action.targetEnemyId().equals(result.targetEnemyId())) throw new IllegalArgumentException("저장된 AttackResult가 action target과 일치하지 않습니다.");
        if (result.statType() != ATTACK_STAT || result.statModifier() != inputs.statModifier() || result.damageStatModifier() != inputs.statModifier()
                || result.equipmentAttackModifier() != inputs.equipmentAttackModifier() || result.equipmentDamageModifier() != inputs.equipmentDamageModifier()) {
            throw new IllegalArgumentException("저장된 AttackResult modifier가 현재 canonical state와 일치하지 않습니다.");
        }
        if (result.defenseScore() != inputs.enemy().combatProfile().defenseScore() || result.damageMitigation() != inputs.enemy().combatProfile().damageReduction()) throw new IllegalArgumentException("저장된 AttackResult defense가 현재 target과 일치하지 않습니다.");
        if (result.targetHpBefore() != inputs.enemy().vitals().hp().current()) throw new IllegalArgumentException("저장된 AttackResult target HP가 현재 canonical state와 일치하지 않습니다.");
    }

    private Inputs inputs(GameState state, AttackAction action) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        Objects.requireNonNull(action, "AttackAction은 필수입니다.");
        CombatEncounter encounter = state.combatEncounter();
        if (encounter == null || !encounter.active()) throw new IllegalArgumentException("활성 combat encounter가 필요합니다.");
        if (!CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())) throw new IllegalArgumentException("현재 combat actor가 player가 아닙니다.");
        if (state.playerCharacter().vitals().incapacitated()) throw new IllegalArgumentException("행동 불가능한 player는 공격할 수 없습니다.");
        if (!encounter.encounterId().equals(action.encounterId())) throw new IllegalArgumentException("AttackAction encounterId가 현재 encounter와 일치하지 않습니다.");
        EnemyState enemy = encounter.requireEnemy(action.targetEnemyId());
        if (enemy.defeated()) throw new IllegalArgumentException("defeated enemy는 공격 대상으로 선택할 수 없습니다.");
        EquipmentTotals equipment = equipmentTotals(state.inventory());
        int statModifier = state.playerCharacter().stats().modifier(ATTACK_STAT);
        return new Inputs(enemy, statModifier, equipment.attackBonus(), equipment.damageBonus());
    }

    private EquipmentTotals equipmentTotals(Inventory inventory) {
        int attackBonus = 0;
        int damageBonus = 0;
        for (String itemId : inventory.equipment().slots().values()) {
            ItemCombatModifiers modifiers = inventory.requireItem(itemId).definition().combatModifiers();
            attackBonus = Math.addExact(attackBonus, modifiers.attackBonus());
            damageBonus = Math.addExact(damageBonus, modifiers.damageBonus());
        }
        return new EquipmentTotals(attackBonus, damageBonus);
    }

    private int exactInt(long value, String fieldName) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException(fieldName + "이 지원 범위를 벗어났습니다.");
        return (int) value;
    }

    private record Inputs(EnemyState enemy, int statModifier, int equipmentAttackModifier, int equipmentDamageModifier) {}
    private record EquipmentTotals(int attackBonus, int damageBonus) {}
}
