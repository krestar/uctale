package com.uctale.uctale.domain.game;

import java.util.Objects;

public record NpcRelationship(
        NpcIdentity npc,
        int affinity,
        RelationshipStage stage,
        RelationshipChangeReason lastChangeReason,
        int lastSourceTurn,
        String lastSourceKey,
        int lastAppliedDelta,
        NpcNarrativeMemory narrativeMemory
) {
    public static final int MIN_AFFINITY = -100;
    public static final int MAX_AFFINITY = 100;

    public NpcRelationship {
        Objects.requireNonNull(npc, "NPC identity는 필수입니다.");
        if (affinity < MIN_AFFINITY || affinity > MAX_AFFINITY) throw new IllegalArgumentException("affinity가 허용 범위를 벗어났습니다: " + affinity);
        Objects.requireNonNull(stage, "relationship stage는 필수입니다.");
        if (stage != RelationshipStage.fromAffinity(affinity)) throw new IllegalArgumentException("relationship stage가 affinity와 일치하지 않습니다.");
        narrativeMemory = narrativeMemory == null ? NpcNarrativeMemory.empty() : narrativeMemory;
        boolean hasChange = lastChangeReason != null || lastSourceTurn != 0 || lastSourceKey != null || lastAppliedDelta != 0;
        if (hasChange) {
            Objects.requireNonNull(lastChangeReason, "lastChangeReason은 필수입니다.");
            if (lastSourceTurn < 1 || lastSourceKey == null || lastSourceKey.isBlank() || lastAppliedDelta == 0) {
                throw new IllegalArgumentException("마지막 관계 변화 metadata가 올바르지 않습니다.");
            }
        }
    }

    public static NpcRelationship neutral(NpcIdentity npc) {
        return new NpcRelationship(npc, 0, RelationshipStage.NEUTRAL, null, 0, null, 0, NpcNarrativeMemory.empty());
    }

    public NpcRelationship withMemory(NpcNarrativeMemory memory) {
        return new NpcRelationship(npc, affinity, stage, lastChangeReason, lastSourceTurn, lastSourceKey,
                lastAppliedDelta, Objects.requireNonNull(memory, "NPC narrative memory는 필수입니다."));
    }

    public boolean alreadyApplied(int sourceTurn, String sourceKey) {
        return lastSourceTurn == sourceTurn && Objects.equals(lastSourceKey, sourceKey);
    }

    public NpcRelationship applyDelta(int delta, RelationshipChangeReason reason, int sourceTurn, String sourceKey) {
        if (delta == 0) throw new IllegalArgumentException("relationship delta는 0일 수 없습니다.");
        Objects.requireNonNull(reason, "relationship change reason은 필수입니다.");
        if (sourceTurn < 1 || sourceKey == null || sourceKey.isBlank()) throw new IllegalArgumentException("relationship source metadata가 올바르지 않습니다.");
        long raw = (long) affinity + delta;
        int nextAffinity = (int) Math.max(MIN_AFFINITY, Math.min(MAX_AFFINITY, raw));
        if (nextAffinity == affinity) return this;
        int appliedDelta = nextAffinity - affinity;
        return new NpcRelationship(npc, nextAffinity, RelationshipStage.fromAffinity(nextAffinity), reason,
                sourceTurn, sourceKey, appliedDelta, narrativeMemory);
    }
}
