package com.uctale.uctale.domain.game;

import java.util.Objects;

public record CharacterStats(
        int might,
        int agility,
        int intellect,
        int will,
        int presence
) {
    public static final int DEFAULT_SCORE = 10;
    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 30;

    public CharacterStats {
        validateScore("might", might);
        validateScore("agility", agility);
        validateScore("intellect", intellect);
        validateScore("will", will);
        validateScore("presence", presence);
    }

    public static CharacterStats defaults() {
        return new CharacterStats(DEFAULT_SCORE, DEFAULT_SCORE, DEFAULT_SCORE, DEFAULT_SCORE, DEFAULT_SCORE);
    }

    public int score(StatType statType) {
        Objects.requireNonNull(statType, "statType은 필수입니다.");
        return switch (statType) {
            case MIGHT -> might;
            case AGILITY -> agility;
            case INTELLECT -> intellect;
            case WILL -> will;
            case PRESENCE -> presence;
        };
    }

    public int modifier(StatType statType) {
        return Math.floorDiv(score(statType) - 10, 2);
    }

    private static void validateScore(String name, int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(name + " 능력치는 " + MIN_SCORE + "~" + MAX_SCORE + " 범위여야 합니다.");
        }
    }
}
