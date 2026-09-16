package com.uctale.uctale.domain.game;

import java.util.Objects;

public record RelationshipChangeStamp(RelationshipChangeReason reason, int requestedDelta, int appliedDelta) {
    public RelationshipChangeStamp {
        Objects.requireNonNull(reason, "relationship change reason은 필수입니다.");
        if (requestedDelta == 0) throw new IllegalArgumentException("requested relationship delta는 0일 수 없습니다.");
        if (Math.abs((long) appliedDelta) > NpcRelationship.MAX_AFFINITY - NpcRelationship.MIN_AFFINITY) {
            throw new IllegalArgumentException("applied relationship delta가 허용 범위를 벗어났습니다.");
        }
        if (appliedDelta != 0 && Integer.signum(appliedDelta) != Integer.signum(requestedDelta)) {
            throw new IllegalArgumentException("applied relationship delta 방향이 requested delta와 일치하지 않습니다.");
        }
    }
}
