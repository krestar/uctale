package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.NpcRelationship;
import com.uctale.uctale.domain.game.RelationshipChangeReason;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

@Component
public final class RelationshipAuditCodec {
    private final ObjectMapper objectMapper;

    public RelationshipAuditCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<GameResult.StateChange> stateChanges) {
        ArrayNode root = JsonNodeFactory.instance.arrayNode();
        try {
            if (stateChanges != null) {
                for (GameResult.StateChange change : stateChanges) {
                    if (!(change instanceof GameResult.RelationshipChanged relationship)) continue;
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put("type", "RELATIONSHIP_CHANGED");
                    node.put("reason", relationship.reason().name());
                    node.put("sourceTurn", relationship.sourceTurn());
                    node.put("sourceKey", relationship.sourceKey());
                    if (relationship.previousValue() == null) node.putNull("previousValue");
                    else node.set("previousValue", objectMapper.readTree(objectMapper.writeValueAsString(relationship.previousValue())));
                    node.set("nextValue", objectMapper.readTree(objectMapper.writeValueAsString(relationship.nextValue())));
                    root.add(node);
                }
            }
            return root.isEmpty() ? null : objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("relationship GameLog audit 직렬화에 실패했습니다.", exception);
        }
    }

    public List<GameResult.StateChange> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) throw new IllegalStateException("relationship GameLog audit는 JSON array여야 합니다.");
            List<GameResult.StateChange> changes = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject() || !"RELATIONSHIP_CHANGED".equals(requiredText(node, "type"))) {
                    throw new IllegalStateException("지원하지 않는 relationship audit entry입니다.");
                }
                JsonNode previousNode = node.get("previousValue");
                if (previousNode == null) throw new IllegalStateException("relationship audit previousValue 필드가 필요합니다.");
                NpcRelationship previous = previousNode.isNull() ? null
                        : objectMapper.readValue(requiredObject(node, "previousValue").toString(), NpcRelationship.class);
                NpcRelationship next = objectMapper.readValue(requiredObject(node, "nextValue").toString(), NpcRelationship.class);
                changes.add(new GameResult.RelationshipChanged(previous, next,
                        RelationshipChangeReason.valueOf(requiredText(node, "reason")), requiredInt(node, "sourceTurn"),
                        requiredText(node, "sourceKey")));
            }
            return List.copyOf(changes);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("relationship GameLog audit 역직렬화에 실패했습니다.", exception);
        }
    }

    private JsonNode requiredObject(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isObject()) throw new IllegalStateException("relationship audit " + fieldName + " object가 필요합니다.");
        return value;
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalStateException("relationship audit " + fieldName + " 문자열이 필요합니다.");
        return value.asText();
    }

    private int requiredInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isIntegralNumber()) throw new IllegalStateException("relationship audit " + fieldName + " 정수가 필요합니다.");
        long number = value.asLong();
        if (number < 1 || number > Integer.MAX_VALUE) throw new IllegalStateException("relationship audit " + fieldName + " 범위가 올바르지 않습니다.");
        return (int) number;
    }
}
