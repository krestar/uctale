package com.uctale.uctale.domain.game;

import java.util.Objects;

public record CanonicalFact(String key, String value) {

    public CanonicalFact {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("canonical fact key는 비어 있을 수 없습니다.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canonical fact value는 비어 있을 수 없습니다.");
        }
        key = key.trim();
        value = value.trim();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
    }
}
