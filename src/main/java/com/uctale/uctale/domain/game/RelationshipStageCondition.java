package com.uctale.uctale.domain.game;

import java.util.Objects;

public record RelationshipStageCondition(String npcInstanceId, RelationshipStage minimumStage) {
    public RelationshipStageCondition {
        if (npcInstanceId == null || npcInstanceId.isBlank()) throw new IllegalArgumentException("NPC instanceId는 필수입니다.");
        Objects.requireNonNull(minimumStage, "minimum relationship stage는 필수입니다.");
    }

    public boolean matches(GameState state) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        return state.relationshipState().meetsStage(npcInstanceId, minimumStage);
    }
}
