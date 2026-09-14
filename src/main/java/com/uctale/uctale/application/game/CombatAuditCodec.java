package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.AbilityResult;
import com.uctale.uctale.domain.game.AttackResult;
import com.uctale.uctale.domain.game.CombatChangeReason;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.EnemyCombatProfile;
import com.uctale.uctale.domain.game.GameResult;
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
public final class CombatAuditCodec {
    private final ObjectMapper objectMapper;

    public CombatAuditCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<GameResult.StateChange> stateChanges) {
        ArrayNode root = JsonNodeFactory.instance.arrayNode();
        try {
            if (stateChanges != null) {
                for (GameResult.StateChange change : stateChanges) {
                    if (change instanceof GameResult.AttackResolved attack) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "ATTACK_RESOLVED");
                        node.set("result", objectMapper.readTree(objectMapper.writeValueAsString(attack.result())));
                        root.add(node);
                    } else if (change instanceof GameResult.AbilityResolved ability) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "ABILITY_RESOLVED");
                        node.set("result", objectMapper.readTree(objectMapper.writeValueAsString(ability.result())));
                        root.add(node);
                    } else if (change instanceof GameResult.AbilityCooldownChanged cooldown) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "ABILITY_COOLDOWN_CHANGED");
                        node.put("definitionId", cooldown.definitionId());
                        node.put("previousRemainingTurns", cooldown.previousRemainingTurns());
                        node.put("nextRemainingTurns", cooldown.nextRemainingTurns());
                        node.put("reason", cooldown.reason().name());
                        root.add(node);
                    } else if (change instanceof GameResult.CombatEncounterChanged combat) {
                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put("type", "COMBAT_ENCOUNTER_CHANGED");
                        node.put("reason", combat.reason().name());
                        if (combat.previousEncounter() == null) node.putNull("previousEncounter");
                        else node.set("previousEncounter", objectMapper.readTree(objectMapper.writeValueAsString(combat.previousEncounter())));
                        node.set("nextEncounter", objectMapper.readTree(objectMapper.writeValueAsString(combat.nextEncounter())));
                        root.add(node);
                    }
                }
            }
            return root.isEmpty() ? null : objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("combat GameLog audit 직렬화에 실패했습니다.", exception);
        }
    }

    public List<GameResult.StateChange> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) throw new IllegalStateException("combat GameLog audit는 JSON array여야 합니다.");
            List<GameResult.StateChange> changes = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) throw new IllegalStateException("지원하지 않는 combat audit entry입니다.");
                String type = requiredText(node, "type");
                if ("ATTACK_RESOLVED".equals(type)) {
                    AttackResult result = objectMapper.readValue(requiredObject(node, "result").toString(), AttackResult.class);
                    changes.add(new GameResult.AttackResolved(result));
                } else if ("ABILITY_RESOLVED".equals(type)) {
                    AbilityResult result = objectMapper.readValue(requiredObject(node, "result").toString(), AbilityResult.class);
                    changes.add(new GameResult.AbilityResolved(result));
                } else if ("ABILITY_COOLDOWN_CHANGED".equals(type)) {
                    changes.add(new GameResult.AbilityCooldownChanged(
                            requiredText(node, "definitionId"), requiredInt(node, "previousRemainingTurns"),
                            requiredInt(node, "nextRemainingTurns"),
                            GameResult.AbilityCooldownChangeReason.valueOf(requiredText(node, "reason"))));
                } else if ("COMBAT_ENCOUNTER_CHANGED".equals(type)) {
                    JsonNode previousNode = node.get("previousEncounter");
                    if (previousNode == null) throw new IllegalStateException("combat audit previousEncounter 필드가 필요합니다.");
                    CombatEncounter previous = previousNode.isNull() ? null : decodeEncounter(previousNode);
                    CombatEncounter next = decodeEncounter(requiredObject(node, "nextEncounter"));
                    CombatChangeReason reason = CombatChangeReason.valueOf(requiredText(node, "reason"));
                    changes.add(new GameResult.CombatEncounterChanged(previous, next, reason));
                } else {
                    throw new IllegalStateException("지원하지 않는 combat audit entry입니다: " + type);
                }
            }
            return List.copyOf(changes);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("combat GameLog audit 역직렬화에 실패했습니다.", exception);
        }
    }

    private CombatEncounter decodeEncounter(JsonNode node) throws JacksonException {
        JsonNode normalized = node.deepCopy();
        JsonNode enemies = normalized.get("enemies");
        if (enemies instanceof ObjectNode enemyMap) {
            for (var entry : enemyMap.properties()) {
                if (entry.getValue() instanceof ObjectNode enemy && !enemy.has("combatProfile")) {
                    ObjectNode profile = JsonNodeFactory.instance.objectNode();
                    profile.put("defenseScore", EnemyCombatProfile.DEFAULT_DEFENSE_SCORE);
                    profile.put("damageReduction", EnemyCombatProfile.DEFAULT_DAMAGE_REDUCTION);
                    enemy.set("combatProfile", profile);
                }
            }
        }
        return objectMapper.readValue(normalized.toString(), CombatEncounter.class);
    }

    private JsonNode requiredObject(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isObject()) throw new IllegalStateException("combat audit " + fieldName + " object가 필요합니다.");
        return value;
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalStateException("combat audit " + fieldName + " 문자열이 필요합니다.");
        return value.asText();
    }

    private int requiredInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isIntegralNumber()) throw new IllegalStateException("combat audit " + fieldName + " 정수가 필요합니다.");
        long number = value.asLong();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalStateException("combat audit " + fieldName + " 범위가 올바르지 않습니다.");
        return (int) number;
    }
}
