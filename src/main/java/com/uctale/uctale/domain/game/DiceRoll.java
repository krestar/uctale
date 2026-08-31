package com.uctale.uctale.domain.game;

public record DiceRoll(int value) {
    public static final int MIN_D20 = 1;
    public static final int MAX_D20 = 20;

    public DiceRoll {
        if (value < MIN_D20 || value > MAX_D20) {
            throw new IllegalArgumentException("d20 결과는 1~20 범위여야 합니다.");
        }
    }

    public static DiceRoll d20(RandomSource randomSource) {
        if (randomSource == null) {
            throw new IllegalArgumentException("RandomSource는 필수입니다.");
        }
        return new DiceRoll(randomSource.nextIntInclusive(MIN_D20, MAX_D20));
    }
}
