package com.uctale.uctale.domain.game;

public record EventFlag(String key, String value, int version) implements GameFlag {
    public EventFlag {
        WorldFlag.validate(key, value, version);
        value = value.trim();
    }

    @Override public FlagNamespace namespace() { return FlagNamespace.EVENT; }

    @Override public EventFlag next(String nextValue) {
        if (nextValue == null || nextValue.isBlank()) throw new IllegalArgumentException("flag value는 비어 있을 수 없습니다.");
        String normalized = nextValue.trim();
        if (value.equals(normalized)) throw new IllegalArgumentException("flag value는 실제로 변경되어야 합니다.");
        if (version == Integer.MAX_VALUE) throw new IllegalArgumentException("flag version이 지원 범위를 벗어났습니다.");
        return new EventFlag(key, normalized, version + 1);
    }
}
