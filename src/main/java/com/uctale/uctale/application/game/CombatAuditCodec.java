package com.uctale.uctale.application.game;

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
    public CombatAuditCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    public String serialize(List<GameResult.StateChange> stateChanges) {
        ArrayNode root = JsonNodeFactory.instance.arrayNode();
        try {
            if (stateChanges != null) for (GameResult.StateChange change : stateChanges) {
                if (change instanceof GameResult.AttackResolved attack) { ObjectNode node = JsonNodeFactory.instance.objectNode(); node.put("type","ATTACK_RESOLVED"); node.set("result", objectMapper.readTree(objectMapper.writeValueAsString(attack.result()))); root.add(node); }
                else if (change instanceof GameResult.CombatEncounterChanged combat) { ObjectNode node = JsonNodeFactory.instance.objectNode(); node.put("type","COMBAT_ENCOUNTER_CHANGED"); node.put("reason",combat.reason().name()); if (combat.previousEncounter()==null) node.putNull("previousEncounter"); else node.set("previousEncounter",objectMapper.readTree(objectMapper.writeValueAsString(combat.previousEncounter()))); node.set("nextEncounter",objectMapper.readTree(objectMapper.writeValueAsString(combat.nextEncounter()))); root.add(node); }
            }
            return root.isEmpty()?null:objectMapper.writeValueAsString(root);
        } catch (JacksonException e) { throw new IllegalStateException("combat GameLog audit 직렬화에 실패했습니다.", e); }
    }
    public List<GameResult.StateChange> deserialize(String json) {
        if (json==null||json.isBlank()) return List.of();
        try {
            JsonNode root=objectMapper.readTree(json); if(root==null||!root.isArray()) throw new IllegalStateException("combat GameLog audit는 JSON array여야 합니다.");
            List<GameResult.StateChange> changes=new ArrayList<>();
            for(JsonNode node:root){ if(!node.isObject()) throw new IllegalStateException("지원하지 않는 combat audit entry입니다."); String type=requiredText(node,"type");
                if("ATTACK_RESOLVED".equals(type)) changes.add(new GameResult.AttackResolved(objectMapper.readValue(requiredObject(node,"result").toString(), AttackResult.class)));
                else if("COMBAT_ENCOUNTER_CHANGED".equals(type)){ JsonNode p=node.get("previousEncounter"); if(p==null) throw new IllegalStateException("combat audit previousEncounter 필드가 필요합니다."); CombatEncounter previous=p.isNull()?null:decodeEncounter(p); CombatEncounter next=decodeEncounter(requiredObject(node,"nextEncounter")); changes.add(new GameResult.CombatEncounterChanged(previous,next,CombatChangeReason.valueOf(requiredText(node,"reason")))); }
                else throw new IllegalStateException("지원하지 않는 combat audit entry입니다: "+type);
            }
            return List.copyOf(changes);
        } catch(JacksonException|IllegalArgumentException e){ throw new IllegalStateException("combat GameLog audit 역직렬화에 실패했습니다.",e); }
    }
    private CombatEncounter decodeEncounter(JsonNode node) throws JacksonException {
        JsonNode normalized=node.deepCopy(); JsonNode enemies=normalized.get("enemies");
        if(enemies instanceof ObjectNode enemyMap) for(var entry:enemyMap.properties()) if(entry.getValue() instanceof ObjectNode enemy && !enemy.has("combatProfile")){ ObjectNode profile=JsonNodeFactory.instance.objectNode(); profile.put("defenseScore", EnemyCombatProfile.DEFAULT_DEFENSE_SCORE); profile.put("damageReduction",EnemyCombatProfile.DEFAULT_DAMAGE_REDUCTION); enemy.set("combatProfile",profile); }
        return objectMapper.readValue(normalized.toString(),CombatEncounter.class);
    }
    private JsonNode requiredObject(JsonNode node,String field){JsonNode v=node.get(field);if(v==null||!v.isObject())throw new IllegalStateException("combat audit "+field+" object가 필요합니다.");return v;}
    private String requiredText(JsonNode node,String field){JsonNode v=node.get(field);if(v==null||!v.isTextual()||v.asText().isBlank())throw new IllegalStateException("combat audit "+field+" 문자열이 필요합니다.");return v.asText();}
}
