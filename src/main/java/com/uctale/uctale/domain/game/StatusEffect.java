package com.uctale.uctale.domain.game;

import java.util.Objects;

public record StatusEffect(
        String definitionId,
        int stacks,
        int intensity,
        int remainingTurns,
        StatusExpiryTrigger expiryTrigger,
        boolean incapacitating
) {
    public StatusEffect {
        definitionId = definitionId == null ? "" : definitionId.trim();
        if (definitionId.isBlank()) {
            throw new IllegalArgumentException("status effect definitionId는 비어 있을 수 없습니다.");
        }
        if (stacks < 1) {
            throw new IllegalArgumentException("status effect stacks는 1 이상이어야 합니다.");
        }
        if (intensity < 1) {
            throw new IllegalArgumentException("status effect intensity는 1 이상이어야 합니다.");
        }
        if (remainingTurns < 1) {
            throw new IllegalArgumentException("status effect remainingTurns는 1 이상이어야 합니다.");
        }
        Objects.requireNonNull(expiryTrigger, "status effect expiryTrigger는 필수입니다.");
    }

    public StatusEffect withRuntime(int nextStacks, int nextIntensity, int nextRemainingTurns) {
        return new StatusEffect(
                definitionId,
                nextStacks,
                nextIntensity,
                nextRemainingTurns,
                expiryTrigger,
                incapacitating
        );
    }
}
