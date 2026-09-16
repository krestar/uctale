package com.uctale.uctale.domain.game;

import java.util.Objects;

public record CanonicalFact(
        String key,
        String value,
        int sourceTurn,
        CanonicalFactStatus status
) {

    public CanonicalFact {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("canonical fact key는 비어 있을 수 없습니다.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canonical fact value는 비어 있을 수 없습니다.");
        }
        if (sourceTurn < 1) {
            throw new IllegalArgumentException("canonical fact sourceTurn은 1 이상이어야 합니다.");
        }
        key = key.trim();
        value = value.trim();
        Objects.requireNonNull(status, "canonical fact status는 필수입니다.");
    }

    public CanonicalFact(String key, String value) {
        this(key, value, 1, CanonicalFactStatus.ACTIVE);
    }

    public CanonicalFact(String key, String value, int sourceTurn) {
        this(key, value, sourceTurn, CanonicalFactStatus.ACTIVE);
    }

    public CanonicalFact superseded() {
        if (status == CanonicalFactStatus.SUPERSEDED) {
            return this;
        }
        return new CanonicalFact(key, value, sourceTurn, CanonicalFactStatus.SUPERSEDED);
    }
}
