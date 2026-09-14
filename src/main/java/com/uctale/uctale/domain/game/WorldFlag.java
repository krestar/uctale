package com.uctale.uctale.domain.game;

import java.util.regex.Pattern;

public record WorldFlag(String key, String value, int version) implements GameFlag {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public WorldFlag {
        validate(key, value, version);
        value = value.trim();
    }

    @Override public FlagNamespace namespace() { return FlagNamespace.WORLD; }

    @Override public WorldFlag next(String nextValue) {
        if (nextValue == null || nextValue.isBlank()) throw new IllegalArgumentException("flag value는 비어 있을 수 없습니다.");
        String normalized = nextValue.trim();
        if (value.equals(normalized)) throw new IllegalArgumentException("flag value는 실제로 변경되어야 합니다.");
        if (version == Integer.MAX_VALUE) throw new IllegalArgumentException("flag version이 지원 범위를 벗어났습니다.");
        return new WorldFlag(key, normalized, version + 1);
    }

    static void validate(String key, String value, int version) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) throw new IllegalArgumentException("flag key가 올바르지 않습니다.");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("flag value는 비어 있을 수 없습니다.");
        if (version < 1) throw new IllegalArgumentException("flag version은 1 이상이어야 합니다.");
    }
}
