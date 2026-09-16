package com.uctale.uctale.domain.game;

public record NpcIdentity(String definitionId, String instanceId) {
    public NpcIdentity {
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("NPC definitionId는 필수입니다.");
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("NPC instanceId는 필수입니다.");
    }
}
