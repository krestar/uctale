package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.RandomSource;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureRandomSource implements RandomSource {

    private final SecureRandom secureRandom;

    public SecureRandomSource() {
        this(new SecureRandom());
    }

    SecureRandomSource(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public int nextIntInclusive(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("랜덤 범위의 최소값은 최대값보다 클 수 없습니다.");
        }
        long bound = (long) maxInclusive - minInclusive + 1L;
        if (bound > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("랜덤 범위가 너무 큽니다.");
        }
        return secureRandom.nextInt((int) bound) + minInclusive;
    }
}
