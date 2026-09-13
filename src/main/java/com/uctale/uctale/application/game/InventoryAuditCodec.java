package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.EquipmentSlot;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.ItemCombatModifiers;
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
        ArrayNode root=JsonNodeFactory.instance.arrayNode(); if(stateChanges!=null) for(GameResult.StateChange change:stateChanges){ObjectNode e=encode(change);if(e!=null)root.add(e);} if(root.isEmpty())return null;
        try{return objectMapper.writeValueAsString(root);}catch(JacksonException e){throw new IllegalStateException("inventory GameLog audit 직렬화에 실패했습니다.",e);}
    }
    public List<GameResult.StateChange> deserialize(String json){if(json==null||json.isBlank())return List.of();try{JsonNode root=objectMapper.readTree(json);if(root==null||!root.isArray())throw new IllegalStateException("inventory GameLog audit는 JSON array여야 합니다.");List<GameResult.StateChange> changes=new ArrayList<>();for(JsonNode node:root){if(!node.isObject())throw new IllegalStateException("inventory GameLog audit entry는 object여야 합니다.");changes.add(decode(node));}return List.copyOf(changes);}catch(JacksonException|IllegalArgumentException e){throw new IllegalStateException("inventory GameLog audit 역직렬화에 실패했습니다.",e);}}
    private ObjectNode encode(GameResult.StateChange change){
        if(change instanceof GameResult.ItemAcquired c){ObjectNode n=typed("ITEM_ACQUIRED");n.set("item",encodeItem(c.item()));n.put("resultingQuantity",c.resultingQuantity());return n;}
        if(change instanceof GameResult.ItemRemoved c){ObjectNode n=typed("ITEM_REMOVED");n.set("item",encodeItem(c.item()));return n;}
        if(change instanceof GameResult.ItemQuantityChanged c){ObjectNode n=itemReference("ITEM_QUANTITY_CHANGED",c.itemId(),c.definitionId());n.put("previousQuantity",c.previousQuantity());n.put("nextQuantity",c.nextQuantity());return n;}
        if(change instanceof GameResult.ItemConsumed c){ObjectNode n=itemReference("ITEM_CONSUMED",c.itemId(),c.definitionId());n.put("quantity",c.quantity());n.put("remainingQuantity",c.remainingQuantity());return n;}
        if(change instanceof GameResult.ItemEquipped c){ObjectNode n=itemReference("ITEM_EQUIPPED",c.itemId(),c.definitionId());n.put("slot",c.slot().name());return n;}
        if(change instanceof GameResult.ItemUnequipped c){ObjectNode n=itemReference("ITEM_UNEQUIPPED",c.itemId(),c.definitionId());n.put("slot",c.slot().name());return n;}return null;}
    private GameResult.StateChange decode(JsonNode n){return switch(requiredText(n,"type")){case "ITEM_ACQUIRED"->new GameResult.ItemAcquired(decodeItem(requiredObject(n,"item")),requiredPositiveInt(n,"resultingQuantity"));case "ITEM_REMOVED"->new GameResult.ItemRemoved(decodeItem(requiredObject(n,"item")));case "ITEM_QUANTITY_CHANGED"->new GameResult.ItemQuantityChanged(requiredText(n,"itemId"),requiredText(n,"definitionId"),requiredPositiveInt(n,"previousQuantity"),requiredPositiveInt(n,"nextQuantity"));case "ITEM_CONSUMED"->new GameResult.ItemConsumed(requiredText(n,"itemId"),requiredText(n,"definitionId"),requiredPositiveInt(n,"quantity"),requiredNonNegativeInt(n,"remainingQuantity"));case "ITEM_EQUIPPED"->new GameResult.ItemEquipped(requiredSlot(n,"slot"),requiredText(n,"itemId"),requiredText(n,"definitionId"));case "ITEM_UNEQUIPPED"->new GameResult.ItemUnequipped(requiredSlot(n,"slot"),requiredText(n,"itemId"),requiredText(n,"definitionId"));default->throw new IllegalStateException("지원하지 않는 inventory audit type입니다: "+requiredText(n,"type"));};}
    private ObjectNode encodeItem(OwnedItem item){ObjectNode n=JsonNodeFactory.instance.objectNode();n.put("id",item.id());n.put("quantity",item.quantity());ObjectNode d=JsonNodeFactory.instance.objectNode();d.put("id",item.definition().id());d.put("ownershipType",item.definition().ownershipType().name());if(item.definition().equipmentSlot()==null)d.putNull("equipmentSlot");else d.put("equipmentSlot",item.definition().equipmentSlot().name());ObjectNode m=JsonNodeFactory.instance.objectNode();m.put("attackBonus",item.definition().combatModifiers().attackBonus());m.put("damageBonus",item.definition().combatModifiers().damageBonus());d.set("combatModifiers",m);n.set("definition",d);return n;}
    private OwnedItem decodeItem(JsonNode n){JsonNode d=requiredObject(n,"definition");EquipmentSlot slot=null;JsonNode s=d.get("equipmentSlot");if(s!=null&&!s.isNull()){if(!s.isTextual())throw new IllegalStateException("inventory audit equipmentSlot이 문자열이 아닙니다.");slot=EquipmentSlot.valueOf(s.asText());}ItemCombatModifiers m=decodeCombatModifiers(d.get("combatModifiers"));ItemDefinition def=new ItemDefinition(requiredText(d,"id"),ItemOwnershipType.valueOf(requiredText(d,"ownershipType")),slot,m);return new OwnedItem(requiredText(n,"id"),def,requiredPositiveInt(n,"quantity"));}
    private ItemCombatModifiers decodeCombatModifiers(JsonNode n){if(n==null)return ItemCombatModifiers.none();if(!n.isObject())throw new IllegalStateException("inventory audit combatModifiers object가 필요합니다.");return new ItemCombatModifiers(requiredInt(n,"attackBonus"),requiredInt(n,"damageBonus"));}
    private ObjectNode typed(String t){ObjectNode n=JsonNodeFactory.instance.objectNode();n.put("type",t);return n;}private ObjectNode itemReference(String t,String id,String d){ObjectNode n=typed(t);n.put("itemId",id);n.put("definitionId",d);return n;}private JsonNode requiredObject(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||!v.isObject())throw new IllegalStateException("inventory audit "+f+" object가 필요합니다.");return v;}private String requiredText(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||!v.isTextual()||v.asText().isBlank())throw new IllegalStateException("inventory audit "+f+" 문자열이 필요합니다.");return v.asText();}private EquipmentSlot requiredSlot(JsonNode n,String f){return EquipmentSlot.valueOf(requiredText(n,f));}private int requiredPositiveInt(JsonNode n,String f){int v=requiredInt(n,f);if(v<1)throw new IllegalStateException("inventory audit "+f+"은 1 이상이어야 합니다.");return v;}private int requiredNonNegativeInt(JsonNode n,String f){int v=requiredInt(n,f);if(v<0)throw new IllegalStateException("inventory audit "+f+"은 0 이상이어야 합니다.");return v;}private int requiredInt(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||!v.isIntegralNumber())throw new IllegalStateException("inventory audit "+f+" 정수가 필요합니다.");long x=v.asLong();if(x<Integer.MIN_VALUE||x>Integer.MAX_VALUE)throw new IllegalStateException("inventory audit "+f+"이 지원 범위를 벗어났습니다.");return(int)x;}
}
