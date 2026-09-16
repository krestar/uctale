package com.uctale.uctale.domain.game;

public enum RelationshipStage {
    HOSTILE,
    WARY,
    NEUTRAL,
    FRIENDLY,
    TRUSTED;

    public static RelationshipStage fromAffinity(int affinity) {
        if (affinity < NpcRelationship.MIN_AFFINITY || affinity > NpcRelationship.MAX_AFFINITY) {
            throw new IllegalArgumentException("affinity가 허용 범위를 벗어났습니다: " + affinity);
        }
        if (affinity <= -50) return HOSTILE;
        if (affinity <= -20) return WARY;
        if (affinity < 20) return NEUTRAL;
        if (affinity < 50) return FRIENDLY;
        return TRUSTED;
    }

    public boolean atLeast(RelationshipStage minimum) {
        if (minimum == null) throw new IllegalArgumentException("minimum relationship stage는 필수입니다.");
        return ordinal() >= minimum.ordinal();
    }
}
