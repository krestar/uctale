package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.DiceRoll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomSourceTest {

    @Test
    @DisplayName("production SecureRandom adapter는 요청 범위 안의 값을 반환한다")
    void secureRandomSource_ReturnsValueWithinRequestedRange() {
        SecureRandomSource randomSource = new SecureRandomSource();

        for (int i = 0; i < 100; i++) {
            int value = randomSource.nextIntInclusive(DiceRoll.MIN_D20, DiceRoll.MAX_D20);
            assertThat(value).isBetween(DiceRoll.MIN_D20, DiceRoll.MAX_D20);
        }
    }
}
