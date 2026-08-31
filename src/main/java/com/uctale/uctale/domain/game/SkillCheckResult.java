package com.uctale.uctale.domain.game;

import java.util.Objects;

public record SkillCheckResult(
        StatType statType,
        int rawRoll,
        int statModifier,
        int situationalModifier,
        int dc,
        int total,
        SkillCheckOutcome outcome,
        int rulesetVersion
) {
    public SkillCheckResult {
        Objects.requireNonNull(statType, "statType은 필수입니다.");
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        new DiceRoll(rawRoll);
        new Difficulty(dc);
        if (statModifier < CharacterStats.MIN_MODIFIER || statModifier > CharacterStats.MAX_MODIFIER) {
            throw new IllegalArgumentException("stat modifier가 허용 범위를 벗어났습니다.");
        }
        if (situationalModifier < SkillCheck.MIN_SITUATIONAL_MODIFIER
                || situationalModifier > SkillCheck.MAX_SITUATIONAL_MODIFIER) {
            throw new IllegalArgumentException("상황 modifier가 허용 범위를 벗어났습니다.");
        }
        if (rulesetVersion != SkillCheck.RULESET_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 Skill Check rulesetVersion입니다: " + rulesetVersion);
        }
        if (total != rawRoll + statModifier + situationalModifier) {
            throw new IllegalArgumentException("Skill Check total이 계산 근거와 일치하지 않습니다.");
        }
        SkillCheckOutcome expected = total >= dc ? SkillCheckOutcome.SUCCESS : SkillCheckOutcome.FAILURE;
        if (outcome != expected) {
            throw new IllegalArgumentException("Skill Check outcome이 total/DC와 일치하지 않습니다.");
        }
    }
}
