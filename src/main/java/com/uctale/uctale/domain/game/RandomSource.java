package com.uctale.uctale.domain.game;

public interface RandomSource {
    int nextIntInclusive(int minInclusive, int maxInclusive);
}
