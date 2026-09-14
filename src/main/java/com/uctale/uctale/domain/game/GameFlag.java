package com.uctale.uctale.domain.game;

public sealed interface GameFlag permits WorldFlag, EventFlag {
    String key();
    String value();
    int version();
    FlagNamespace namespace();
    GameFlag next(String nextValue);
}
