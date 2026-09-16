package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record NpcRelationship(
        NpcIdentity npc,
        int affinity,
        RelationshipStage stage,
        RelationshipChangeReason lastChangeReason,
        int lastSourceTurn,
        String lastSourceKey,
        int lastRequestedDelta,
        int lastAppliedDelta,
        Map<String, RelationshipChangeStamp> lastTurnChanges,
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
        if (lastTurnChanges == null || lastTurnChanges.isEmpty()) lastTurnChanges = Map.of();
        else lastTurnChanges = Collections.unmodifiableMap(new TreeMap<>(lastTurnChanges));
        boolean hasChange = lastChangeReason != null || lastSourceTurn != 0 || lastSourceKey != null
                || lastRequestedDelta != 0 || lastAppliedDelta != 0 || !lastTurnChanges.isEmpty();
        if (hasChange) {
            Objects.requireNonNull(lastChangeReason, "lastChangeReason은 필수입니다.");
            if (lastSourceTurn < 1 || lastSourceKey == null || lastSourceKey.isBlank() || lastRequestedDelta == 0) {
                throw new IllegalArgumentException("마지막 관계 변화 metadata가 올바르지 않습니다.");
            }
            RelationshipChangeStamp lastStamp = lastTurnChanges.get(lastSourceKey);
            if (lastStamp == null || lastStamp.reason() != lastChangeReason
                    || lastStamp.requestedDelta() != lastRequestedDelta || lastStamp.appliedDelta() != lastAppliedDelta) {
                throw new IllegalArgumentException("마지막 관계 변화 metadata와 dedupe ledger가 일치하지 않습니다.");
            }
        } else if (!lastTurnChanges.isEmpty()) {
            throw new IllegalArgumentException("변화 metadata 없이 dedupe ledger만 존재할 수 없습니다.");
        }
    }

    public static NpcRelationship neutral(NpcIdentity npc) {
        return new NpcRelationship(npc, 0, RelationshipStage.NEUTRAL, null, 0, null, 0, 0,
                Map.of(), NpcNarrativeMemory.empty());
    }

    public NpcRelationship withMemory(NpcNarrativeMemory memory) {
        return new NpcRelationship(npc, affinity, stage, lastChangeReason, lastSourceTurn, lastSourceKey,
                lastRequestedDelta, lastAppliedDelta, lastTurnChanges,
                Objects.requireNonNull(memory, "NPC narrative memory는 필수입니다."));
    }

    public boolean alreadyApplied(int sourceTurn, String sourceKey) {
        return sourceTurn == lastSourceTurn && lastTurnChanges.containsKey(sourceKey);
    }

    public void validateRetry(RelationshipCommand command) {
        if (!alreadyApplied(command.sourceTurn(), command.sourceKey())) return;
        RelationshipChangeStamp stamp = lastTurnChanges.get(command.sourceKey());
        if (stamp.reason() != command.reason() || stamp.requestedDelta() != command.delta()) {
            throw new IllegalArgumentException("같은 relationship source key를 다른 변화로 재사용할 수 없습니다.");
        }
    }

    public NpcRelationship applyDelta(int delta, RelationshipChangeReason reason, int sourceTurn, String sourceKey) {
        if (delta == 0) throw new IllegalArgumentException("relationship delta는 0일 수 없습니다.");
        Objects.requireNonNull(reason, "relationship change reason은 필수입니다.");
        if (sourceTurn < 1 || sourceKey == null || sourceKey.isBlank()) throw new IllegalArgumentException("relationship source metadata가 올바르지 않습니다.");
        if (lastSourceTurn > sourceTurn) throw new IllegalArgumentException("과거 turn의 relationship change를 현재 상태에 적용할 수 없습니다.");
        if (alreadyApplied(sourceTurn, sourceKey)) {
            RelationshipChangeStamp stamp = lastTurnChanges.get(sourceKey);
            if (stamp.reason() != reason || stamp.requestedDelta() != delta) {
                throw new IllegalArgumentException("같은 relationship source key를 다른 변화로 재사용할 수 없습니다.");
            }
            return this;
        }

        long raw = (long) affinity + delta;
        int nextAffinity = (int) Math.max(MIN_AFFINITY, Math.min(MAX_AFFINITY, raw));
        int appliedDelta = nextAffinity - affinity;
        TreeMap<String, RelationshipChangeStamp> nextLedger = sourceTurn == lastSourceTurn
                ? new TreeMap<>(lastTurnChanges) : new TreeMap<>();
        nextLedger.put(sourceKey, new RelationshipChangeStamp(reason, delta, appliedDelta));
        return new NpcRelationship(npc, nextAffinity, RelationshipStage.fromAffinity(nextAffinity), reason,
                sourceTurn, sourceKey, delta, appliedDelta, nextLedger, narrativeMemory);
    }
}
