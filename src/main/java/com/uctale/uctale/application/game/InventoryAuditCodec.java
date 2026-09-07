package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.EquipmentSlot;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.ItemDefinition;
import com.uctale.uctale.domain.game.ItemOwnershipType;
import com.uctale.uctale.domain.game.OwnedItem;
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
public final class InventoryAuditCodec {
    private final ObjectMapper objectMapper;
    public InventoryAuditCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String serialize(List<GameResult.StateChange> stateChanges) {
        ArrayNode root = JsonNodeFactory.instance.arrayNode();
        if (stateChanges != null) {
            for (GameResult.StateChange change : stateChanges) {
                ObjectNode encoded = encode(change);
                if (encoded != null) root.add(encoded);
            }
        }
        if (root.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(root); }
        catch (JacksonException exception) { throw new IllegalStateException("inventory GameLog audit 직렬화에 실패했습니다.", exception); }
    }

    public List<GameResult.StateChange> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) throw new IllegalStateException("inventory GameLog audit는 JSON array여야 합니다.");
            List<GameResult.StateChange> changes = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) throw new IllegalStateException("inventory GameLog audit entry는 object여야 합니다.");
                changes.add(decode(node));
            }
            return List.copyOf(changes);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("inventory GameLog audit 역직렬화에 실패했습니다.", exception);
        }
    }

    private ObjectNode encode(GameResult.StateChange change) {
        if (change instanceof GameResult.ItemAcquired acquired) {
            ObjectNode node = typed("ITEM_ACQUIRED"); node.set("item", encodeItem(acquired.item())); node.put("resultingQuantity", acquired.resultingQuantity()); return node;
        }
        if (change instanceof GameResult.ItemRemoved removed) {
            ObjectNode node = typed("ITEM_REMOVED"); node.set("item", encodeItem(removed.item())); return node;
        }
        if (change instanceof GameResult.ItemQuantityChanged c) {
            ObjectNode node = itemReference("ITEM_QUANTITY_CHANGED", c.itemId(), c.definitionId()); node.put("previousQuantity", c.previousQuantity()); node.put("nextQuantity", c.nextQuantity()); return node;
        }
        if (change instanceof GameResult.ItemConsumed c) {
            ObjectNode node = itemReference("ITEM_CONSUMED", c.itemId(), c.definitionId()); node.put("quantity", c.quantity()); node.put("remainingQuantity", c.remainingQuantity()); return node;
        }
        if (change instanceof GameResult.ItemEquipped c) {
            ObjectNode node = itemReference("ITEM_EQUIPPED", c.itemId(), c.definitionId()); node.put("slot", c.slot().name()); return node;
        }
        if (change instanceof GameResult.ItemUnequipped c) {
            ObjectNode node = itemReference("ITEM_UNEQUIPPED", c.itemId(), c.definitionId()); node.put("slot", c.slot().name()); return node;
        }
        return null;
    }

    private GameResult.StateChange decode(JsonNode node) {
        return switch (requiredText(node, "type")) {
            case "ITEM_ACQUIRED" -> new GameResult.ItemAcquired(decodeItem(requiredObject(node, "item")), requiredPositiveInt(node, "resultingQuantity"));
            case "ITEM_REMOVED" -> new GameResult.ItemRemoved(decodeItem(requiredObject(node, "item")));
            case "ITEM_QUANTITY_CHANGED" -> new GameResult.ItemQuantityChanged(requiredText(node, "itemId"), requiredText(node, "definitionId"), requiredPositiveInt(node, "previousQuantity"), requiredPositiveInt(node, "nextQuantity"));
            case "ITEM_CONSUMED" -> new GameResult.ItemConsumed(requiredText(node, "itemId"), requiredText(node, "definitionId"), requiredPositiveInt(node, "quantity"), requiredNonNegativeInt(node, "remainingQuantity"));
            case "ITEM_EQUIPPED" -> new GameResult.ItemEquipped(requiredSlot(node, "slot"), requiredText(node, "itemId"), requiredText(node, "definitionId"));
            case "ITEM_UNEQUIPPED" -> new GameResult.ItemUnequipped(requiredSlot(node, "slot"), requiredText(node, "itemId"), requiredText(node, "definitionId"));
            default -> throw new IllegalStateException("지원하지 않는 inventory audit type입니다: " + requiredText(node, "type"));
        };
    }

    private ObjectNode encodeItem(OwnedItem item) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", item.id()); node.put("quantity", item.quantity());
        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.put("id", item.definition().id()); definition.put("ownershipType", item.definition().ownershipType().name());
        if (item.definition().equipmentSlot() == null) definition.putNull("equipmentSlot"); else definition.put("equipmentSlot", item.definition().equipmentSlot().name());
        node.set("definition", definition); return node;
    }

    private OwnedItem decodeItem(JsonNode node) {
        JsonNode definitionNode = requiredObject(node, "definition");
        EquipmentSlot slot = null;
        JsonNode slotNode = definitionNode.get("equipmentSlot");
        if (slotNode != null && !slotNode.isNull()) {
            if (!slotNode.isTextual()) throw new IllegalStateException("inventory audit equipmentSlot이 문자열이 아닙니다.");
            slot = EquipmentSlot.valueOf(slotNode.asText());
        }
        ItemDefinition definition = new ItemDefinition(requiredText(definitionNode, "id"), ItemOwnershipType.valueOf(requiredText(definitionNode, "ownershipType")), slot);
        return new OwnedItem(requiredText(node, "id"), definition, requiredPositiveInt(node, "quantity"));
    }

    private ObjectNode typed(String type) { ObjectNode node = JsonNodeFactory.instance.objectNode(); node.put("type", type); return node; }
    private ObjectNode itemReference(String type, String itemId, String definitionId) { ObjectNode node = typed(type); node.put("itemId", itemId); node.put("definitionId", definitionId); return node; }
    private JsonNode requiredObject(JsonNode node, String fieldName) { JsonNode value = node.get(fieldName); if (value == null || !value.isObject()) throw new IllegalStateException("inventory audit " + fieldName + " object가 필요합니다."); return value; }
    private String requiredText(JsonNode node, String fieldName) { JsonNode value = node.get(fieldName); if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalStateException("inventory audit " + fieldName + " 문자열이 필요합니다."); return value.asText(); }
    private EquipmentSlot requiredSlot(JsonNode node, String fieldName) { return EquipmentSlot.valueOf(requiredText(node, fieldName)); }
    private int requiredPositiveInt(JsonNode node, String fieldName) { int value = requiredInt(node, fieldName); if (value < 1) throw new IllegalStateException("inventory audit " + fieldName + "은 1 이상이어야 합니다."); return value; }
    private int requiredNonNegativeInt(JsonNode node, String fieldName) { int value = requiredInt(node, fieldName); if (value < 0) throw new IllegalStateException("inventory audit " + fieldName + "은 0 이상이어야 합니다."); return value; }
    private int requiredInt(JsonNode node, String fieldName) { JsonNode value = node.get(fieldName); if (value == null || !value.isIntegralNumber()) throw new IllegalStateException("inventory audit " + fieldName + " 정수가 필요합니다."); long number = value.asLong(); if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalStateException("inventory audit " + fieldName + "이 지원 범위를 벗어났습니다."); return (int) number; }
}
