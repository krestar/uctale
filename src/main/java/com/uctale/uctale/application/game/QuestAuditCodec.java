package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.EventFlag;
import com.uctale.uctale.domain.game.FlagNamespace;
import com.uctale.uctale.domain.game.GameFlag;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.ObjectiveProgress;
import com.uctale.uctale.domain.game.ObjectiveTrigger;
import com.uctale.uctale.domain.game.QuestStatus;
import com.uctale.uctale.domain.game.WorldFlag;
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
public final class QuestAuditCodec {
    private final ObjectMapper objectMapper;

    public QuestAuditCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<GameResult.StateChange> stateChanges) {
        ArrayNode root = JsonNodeFactory.instance.arrayNode();
        try {
            if (stateChanges != null) {
                for (GameResult.StateChange change : stateChanges) {
                    if (change instanceof GameResult.ObjectiveProgressChanged progress) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "OBJECTIVE_PROGRESS_CHANGED");
                        node.put("questDefinitionId", progress.questDefinitionId());
                        node.put("objectiveId", progress.objectiveId());
                        node.put("reason", progress.reason().name());
                        node.set("previousProgress", objectMapper.readTree(objectMapper.writeValueAsString(progress.previousProgress())));
                        node.set("nextProgress", objectMapper.readTree(objectMapper.writeValueAsString(progress.nextProgress())));
                        root.add(node);
                    } else if (change instanceof GameResult.QuestStatusChanged status) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "QUEST_STATUS_CHANGED");
                        node.put("questDefinitionId", status.questDefinitionId());
                        node.put("previousStatus", status.previousStatus().name());
                        node.put("nextStatus", status.nextStatus().name());
                        node.put("reason", status.reason().name());
                        root.add(node);
                    } else if (change instanceof GameResult.FlagChanged flag) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "FLAG_CHANGED");
                        node.put("namespace", flag.namespace().name());
                        node.put("key", flag.key());
                        node.put("reason", flag.reason().name());
                        if (flag.previousValue() == null) node.putNull("previousValue");
                        else node.set("previousValue", flagNode(flag.previousValue()));
                        node.set("nextValue", flagNode(flag.nextValue()));
                        root.add(node);
                    }
                }
            }
            return root.isEmpty() ? null : objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("quest GameLog audit 직렬화에 실패했습니다.", exception);
        }
    }

    public List<GameResult.StateChange> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) throw new IllegalStateException("quest GameLog audit는 JSON array여야 합니다.");
            List<GameResult.StateChange> changes = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) throw new IllegalStateException("지원하지 않는 quest audit entry입니다.");
                String type = requiredText(node, "type");
                if ("OBJECTIVE_PROGRESS_CHANGED".equals(type)) {
                    ObjectiveProgress previous = objectMapper.readValue(requiredObject(node, "previousProgress").toString(), ObjectiveProgress.class);
                    ObjectiveProgress next = objectMapper.readValue(requiredObject(node, "nextProgress").toString(), ObjectiveProgress.class);
                    changes.add(new GameResult.ObjectiveProgressChanged(
                            requiredText(node, "questDefinitionId"), requiredText(node, "objectiveId"), previous, next,
                            ObjectiveTrigger.valueOf(requiredText(node, "reason"))));
                } else if ("QUEST_STATUS_CHANGED".equals(type)) {
                    changes.add(new GameResult.QuestStatusChanged(
                            requiredText(node, "questDefinitionId"),
                            QuestStatus.valueOf(requiredText(node, "previousStatus")),
                            QuestStatus.valueOf(requiredText(node, "nextStatus")),
                            GameResult.QuestStatusChangeReason.valueOf(requiredText(node, "reason"))));
                } else if ("FLAG_CHANGED".equals(type)) {
                    FlagNamespace namespace = FlagNamespace.valueOf(requiredText(node, "namespace"));
                    String key = requiredText(node, "key");
                    JsonNode previousNode = node.get("previousValue");
                    if (previousNode == null) throw new IllegalStateException("quest audit previousValue 필드가 필요합니다.");
                    GameFlag previous = previousNode.isNull() ? null : decodeFlag(namespace, previousNode);
                    GameFlag next = decodeFlag(namespace, requiredObject(node, "nextValue"));
                    changes.add(new GameResult.FlagChanged(namespace, key, previous, next,
                            GameResult.FlagChangeReason.valueOf(requiredText(node, "reason"))));
                } else {
                    throw new IllegalStateException("지원하지 않는 quest audit entry입니다: " + type);
                }
            }
            return List.copyOf(changes);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("quest GameLog audit 역직렬화에 실패했습니다.", exception);
        }
    }

    private ObjectNode flagNode(GameFlag flag) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("key", flag.key());
        node.put("value", flag.value());
        node.put("version", flag.version());
        return node;
    }

    private GameFlag decodeFlag(FlagNamespace namespace, JsonNode node) {
        String key = requiredText(node, "key");
        String value = requiredText(node, "value");
        int version = requiredInt(node, "version");
        return namespace == FlagNamespace.WORLD ? new WorldFlag(key, value, version) : new EventFlag(key, value, version);
    }

    private JsonNode requiredObject(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isObject()) throw new IllegalStateException("quest audit " + fieldName + " object가 필요합니다.");
        return value;
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalStateException("quest audit " + fieldName + " 문자열이 필요합니다.");
        return value.asText();
    }

    private int requiredInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isIntegralNumber()) throw new IllegalStateException("quest audit " + fieldName + " 정수가 필요합니다.");
        long number = value.asLong();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalStateException("quest audit " + fieldName + " 범위가 올바르지 않습니다.");
        return (int) number;
    }
}
