package com.uctale.uctale.domain.game;

public record ResourcePool(int current, int max) {
    public ResourcePool {
        if (max < 1) throw new IllegalArgumentException("resource max는 1 이상이어야 합니다.");
        if (current < 0 || current > max) throw new IllegalArgumentException("resource current는 0 이상 max 이하여야 합니다.");
    }
    public static ResourcePool full(int max) { return new ResourcePool(max,max); }
    public ResourcePool withCurrent(int nextCurrent) { return new ResourcePool(nextCurrent,max); }
}
