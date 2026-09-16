package com.uctale.uctale.domain.game;

import java.util.Objects;

public record RelationshipCommand(
        NpcIdentity npc,
        int delta,
        RelationshipChangeReason reason,
        int sourceTurn,
        String sourceKey
) {
    public RelationshipCommand {
        Objects.requireNonNull(npc, "NPC identity는 필수입니다.");
        if (delta == 0) throw new IllegalArgumentException("relationship delta는 0일 수 없습니다.");
        Objects.requireNonNull(reason, "relationship change reason은 필수입니다.");
        if (sourceTurn < 1) throw new IllegalArgumentException("relationship source turn은 1 이상이어야 합니다.");
        if (sourceKey == null || sourceKey.isBlank()) throw new IllegalArgumentException("relationship source key는 필수입니다.");
    }

    public static RelationshipCommand talk(NpcIdentity npc, int delta, int sourceTurn, String talkKey) {
        return new RelationshipCommand(npc, delta, RelationshipChangeReason.TALK, sourceTurn, "talk:" + requireKey(talkKey));
    }

    public static RelationshipCommand quest(NpcIdentity npc, int delta, int sourceTurn, String questKey) {
        return new RelationshipCommand(npc, delta, RelationshipChangeReason.QUEST, sourceTurn, "quest:" + requireKey(questKey));
    }

    public static RelationshipCommand gameResult(NpcIdentity npc, int delta, int sourceTurn, String resultKey) {
        return new RelationshipCommand(npc, delta, RelationshipChangeReason.GAME_RESULT, sourceTurn, "result:" + requireKey(resultKey));
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("relationship source 식별자는 필수입니다.");
        return key;
    }
}
