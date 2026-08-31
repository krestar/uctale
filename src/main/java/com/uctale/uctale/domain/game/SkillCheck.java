package com.uctale.uctale.domain.game;

import java.util.Objects;

public record SkillCheck(
        StatType statType,
        Difficulty difficulty,
        int situationalModifier
) {
    public static final int RULESET_VERSION = 1;
    public static final int MIN_SITUATIONAL_MODIFIER = -20;
    public static final int MAX_SITUATIONAL_MODIFIER = 20;

    public SkillCheck {
        Objects.requireNonNull(statType, "statType은 필수입니다.");
        Objects.requireNonNull(difficulty, "difficulty는 필수입니다.");
        if (situationalModifier < MIN_SITUATIONAL_MODIFIER || situationalModifier > MAX_SITUATIONAL_MODIFIER) {
            throw new IllegalArgumentException(
                    "상황 modifier는 " + MIN_SITUATIONAL_MODIFIER + "~" + MAX_SITUATIONAL_MODIFIER + " 범위여야 합니다."
            );
        }
    }

    public SkillCheckResult resolve(CharacterStats stats, RandomSource randomSource) {
        Objects.requireNonNull(stats, "stats는 필수입니다.");
        DiceRoll roll = DiceRoll.d20(randomSource);
        int statModifier = stats.modifier(statType);
        int total = roll.value() + statModifier + situationalModifier;
        SkillCheckOutcome outcome = total >= difficulty.dc()
                ? SkillCheckOutcome.SUCCESS
                : SkillCheckOutcome.FAILURE;
        return new SkillCheckResult(
                statType,
                roll.value(),
                statModifier,
                situationalModifier,
                difficulty.dc(),
                total,
                outcome,
                RULESET_VERSION
        );
    }
}
