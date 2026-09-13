package com.uctale.uctale.domain.game;

public record EnemyCombatProfile(int defenseScore, int damageReduction) {
    public static final int DEFAULT_DEFENSE_SCORE = 10;
    public static final int DEFAULT_DAMAGE_REDUCTION = 0;
    public static final int MIN_DEFENSE_SCORE = 1;
    public static final int MAX_DEFENSE_SCORE = 40;
    public static final int MAX_DAMAGE_REDUCTION = 100;

    public EnemyCombatProfile {
        if (defenseScore < MIN_DEFENSE_SCORE || defenseScore > MAX_DEFENSE_SCORE) {
            throw new IllegalArgumentException("defenseScore는 " + MIN_DEFENSE_SCORE + "~" + MAX_DEFENSE_SCORE + " 범위여야 합니다.");
        }
        if (damageReduction < 0 || damageReduction > MAX_DAMAGE_REDUCTION) {
            throw new IllegalArgumentException("damageReduction은 0~" + MAX_DAMAGE_REDUCTION + " 범위여야 합니다.");
        }
    }

    public static EnemyCombatProfile defaults() {
        return new EnemyCombatProfile(DEFAULT_DEFENSE_SCORE, DEFAULT_DAMAGE_REDUCTION);
    }
}
