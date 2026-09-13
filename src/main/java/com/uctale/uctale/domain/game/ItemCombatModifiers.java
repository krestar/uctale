package com.uctale.uctale.domain.game;

public record ItemCombatModifiers(int attackBonus, int damageBonus) {
    public static final int MIN_BONUS = -20;
    public static final int MAX_BONUS = 20;

    public ItemCombatModifiers {
        validate("attackBonus", attackBonus);
        validate("damageBonus", damageBonus);
    }

    public static ItemCombatModifiers none() {
        return new ItemCombatModifiers(0, 0);
    }

    public boolean neutral() {
        return attackBonus == 0 && damageBonus == 0;
    }

    private static void validate(String name, int value) {
        if (value < MIN_BONUS || value > MAX_BONUS) {
            throw new IllegalArgumentException(name + "는 " + MIN_BONUS + "~" + MAX_BONUS + " 범위여야 합니다.");
        }
    }
}
