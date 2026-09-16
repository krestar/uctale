package com.uctale.uctale.domain.game;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record RelationshipState(Map<String, NpcRelationship> relationships) {
    public RelationshipState {
        if (relationships == null || relationships.isEmpty()) {
            relationships = Map.of();
        } else {
            TreeMap<String, NpcRelationship> copy = new TreeMap<>();
            relationships.forEach((instanceId, relationship) -> {
                if (instanceId == null || relationship == null || !instanceId.equals(relationship.npc().instanceId())) {
                    throw new IllegalArgumentException("relationship map key가 NPC instanceId와 일치해야 합니다.");
                }
                copy.put(instanceId, relationship);
            });
            relationships = Collections.unmodifiableMap(copy);
        }
    }

    public static RelationshipState empty() {
        return new RelationshipState(Map.of());
    }

    public NpcRelationship find(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("NPC instanceId는 필수입니다.");
        return relationships.get(instanceId);
    }

    public RelationshipState put(NpcRelationship relationship) {
        Objects.requireNonNull(relationship, "NPC relationship은 필수입니다.");
        NpcRelationship previous = relationships.get(relationship.npc().instanceId());
        if (previous != null && !previous.npc().definitionId().equals(relationship.npc().definitionId())) {
            throw new IllegalArgumentException("같은 NPC instanceId에 다른 definitionId를 연결할 수 없습니다.");
        }
        TreeMap<String, NpcRelationship> next = new TreeMap<>(relationships);
        next.put(relationship.npc().instanceId(), relationship);
        return new RelationshipState(next);
    }

    public boolean meetsStage(String instanceId, RelationshipStage minimum) {
        Objects.requireNonNull(minimum, "minimum relationship stage는 필수입니다.");
        NpcRelationship relationship = find(instanceId);
        return relationship != null && relationship.stage().atLeast(minimum);
    }
}
