package com.uctale.uctale.domain.game;

public record Difficulty(int dc) {
    public static final int MIN_DC = 1;
    public static final int MAX_DC = 40;

    public Difficulty {
        if (dc < MIN_DC || dc > MAX_DC) {
            throw new IllegalArgumentException("DC는 " + MIN_DC + "~" + MAX_DC + " 범위여야 합니다.");
        }
    }
}
