package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.StatusEffect;
import com.uctale.uctale.domain.game.StatusExpiryTrigger;
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
public final class VitalsAuditCodec {
    private final ObjectMapper objectMapper;

    public VitalsAuditCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<GameResult.StateChange> stateChanges) {
        ArrayNode root = JsonNodeFactory.instance.arrayNode();
        if (stateChanges != null) {
            for (GameResult.StateChange change : stateChanges) {
                ObjectNode encoded = encode(change);
                if (encoded != null) root.add(encoded);
            }
        }
        if (root.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("vitals/status GameLog audit 직렬화에 실패했습니다.", exception);
        }
    }

    public List<GameResult.StateChange> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) throw new IllegalStateException("vitals/status GameLog audit는 JSON array여야 합니다.");
            List<GameResult.StateChange> changes = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) throw new IllegalStateException("vitals/status GameLog audit entry는 object여야 합니다.");
                changes.add(decode(node));
            }
            return List.copyOf(changes);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("vitals/status GameLog audit 역직렬화에 실패했습니다.", exception);
        }
    }

    private ObjectNode encode(GameResult.StateChange change) {
        if (change instanceof GameResult.VitalsChanged c) {
            ObjectNode node = typed("VITALS_CHANGED");
            node.put("resource", c.resource().name());
            node.put("previousValue", c.previousValue());
            node.put("nextValue", c.nextValue());
            node.put("delta", c.delta());
            node.put("reason", c.reason().name());
            return node;
        }
        if (change instanceof GameResult.StatusEffectApplied c) {
            ObjectNode node = typed("STATUS_EFFECT_APPLIED");
            node.set("effect", encodeEffect(c.effect()));
            return node;
        }
        if (change instanceof GameResult.StatusEffectUpdated c) {
            ObjectNode node = typed("STATUS_EFFECT_UPDATED");
            node.set("previous", encodeEffect(c.previous()));
            node.set("next", encodeEffect(c.next()));
            return node;
        }
        if (change instanceof GameResult.StatusDurationChanged c) {
            ObjectNode node = typed("STATUS_DURATION_CHANGED");
            node.put("definitionId", c.definitionId());
            node.put("previousRemainingTurns", c.previousRemainingTurns());
            node.put("nextRemainingTurns", c.nextRemainingTurns());
            return node;
        }
        if (change instanceof GameResult.StatusEffectRemoved c) {
            ObjectNode node = typed("STATUS_EFFECT_REMOVED");
            node.set("effect", encodeEffect(c.effect()));
            node.put("reason", c.reason().name());
            return node;
        }
        return null;
    }

    private GameResult.StateChange decode(JsonNode node) {
        return switch (requiredText(node, "type")) {
            case "VITALS_CHANGED" -> new GameResult.VitalsChanged(
                    GameResult.VitalResource.valueOf(requiredText(node, "resource")),
                    requiredNonNegativeInt(node, "previousValue"),
                    requiredNonNegativeInt(node, "nextValue"),
                    requiredInt(node, "delta"),
                    GameResult.VitalsChangeReason.valueOf(requiredText(node, "reason"))
            );
            case "STATUS_EFFECT_APPLIED" -> new GameResult.StatusEffectApplied(decodeEffect(requiredObject(node, "effect")));
            case "STATUS_EFFECT_UPDATED" -> new GameResult.StatusEffectUpdated(
                    decodeEffect(requiredObject(node, "previous")), decodeEffect(requiredObject(node, "next"))
            );
            case "STATUS_DURATION_CHANGED" -> new GameResult.StatusDurationChanged(
                    requiredText(node, "definitionId"),
                    requiredPositiveInt(node, "previousRemainingTurns"),
                    requiredPositiveInt(node, "nextRemainingTurns")
            );
            case "STATUS_EFFECT_REMOVED" -> new GameResult.StatusEffectRemoved(
                    decodeEffect(requiredObject(node, "effect")),
                    GameResult.StatusRemovalReason.valueOf(requiredText(node, "reason"))
            );
            default -> throw new IllegalStateException("지원하지 않는 vitals/status audit type입니다: " + requiredText(node, "type"));
        };
    }

    private ObjectNode encodeEffect(StatusEffect effect) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("definitionId", effect.definitionId());
        node.put("stacks", effect.stacks());
        node.put("intensity", effect.intensity());
        node.put("remainingTurns", effect.remainingTurns());
        node.put("expiryTrigger", effect.expiryTrigger().name());
        node.put("incapacitating", effect.incapacitating());
        return node;
    }

    private StatusEffect decodeEffect(JsonNode node) {
        return new StatusEffect(
                requiredText(node, "definitionId"),
                requiredPositiveInt(node, "stacks"),
                requiredPositiveInt(node, "intensity"),
                requiredPositiveInt(node, "remainingTurns"),
                StatusExpiryTrigger.valueOf(requiredText(node, "expiryTrigger")),
                requiredBoolean(node, "incapacitating")
        );
    }

    private ObjectNode typed(String type) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", type);
        return node;
    }

    private JsonNode requiredObject(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isObject()) throw new IllegalStateException("vitals/status audit " + fieldName + " object가 필요합니다.");
        return value;
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalStateException("vitals/status audit " + fieldName + " 문자열이 필요합니다.");
        return value.asText();
    }

    private boolean requiredBoolean(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isBoolean()) throw new IllegalStateException("vitals/status audit " + fieldName + " boolean이 필요합니다.");
        return value.asBoolean();
    }

    private int requiredPositiveInt(JsonNode node, String fieldName) {
        int value = requiredInt(node, fieldName);
        if (value < 1) throw new IllegalStateException("vitals/status audit " + fieldName + "은 1 이상이어야 합니다.");
        return value;
    }

    private int requiredNonNegativeInt(JsonNode node, String fieldName) {
        int value = requiredInt(node, fieldName);
        if (value < 0) throw new IllegalStateException("vitals/status audit " + fieldName + "은 0 이상이어야 합니다.");
        return value;
    }

    private int requiredInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isIntegralNumber()) throw new IllegalStateException("vitals/status audit " + fieldName + " 정수가 필요합니다.");
        long number = value.asLong();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalStateException("vitals/status audit " + fieldName + "이 지원 범위를 벗어났습니다.");
        return (int) number;
    }
}
