package com.uctale.uctale.application.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateCodecCompatibilityTest {

    private final GameStateCodec codec = new GameStateCodec(new ObjectMapper(), new GameStateUpgrader());

    @Test
    @DisplayName("현재 schema v9에서 stats가 누락된 손상 snapshot은 기본값으로 숨기지 않는다")
    void currentSchemaMissingStats_FailsExplicitly() {
        assertThatThrownBy(() -> codec.deserialize(currentState("\"playerCharacter\":{\"description\":\"캐릭터\",\"vitals\":" + vitals() + "}")))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("역직렬화");
    }

    @Test
    @DisplayName("현재 schema v9에서 inventory가 누락된 손상 snapshot은 빈 inventory로 숨기지 않는다")
    void currentSchemaMissingInventory_FailsExplicitly() {
        String json = "{\"schemaVersion\":9,\"rulesetVersion\":1,\"state\":{\"turnNumber\":1," + player()
                + ",\"worldState\":{\"premise\":\"세계관\",\"flags\":{}},\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]},\"combatEncounter\":null,\"abilityState\":{\"cooldowns\":{}},\"questState\":" + questState() + ",\"relationshipState\":" + relationshipState() + "}}";
        assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("inventory/items");
    }

    @Test
    @DisplayName("현재 schema v9에서 vitals가 누락된 손상 snapshot은 기본 vitals로 숨기지 않는다")
    void currentSchemaMissingVitals_FailsExplicitly() {
        String player = "\"playerCharacter\":{\"description\":\"캐릭터\",\"stats\":" + stats() + "}";
        assertThatThrownBy(() -> codec.deserialize(currentState(player)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("역직렬화");
    }

    @Test
    @DisplayName("현재 schema v9에서 abilityState가 누락되면 empty cooldown으로 숨기지 않는다")
    void currentSchemaMissingAbilityState_FailsExplicitly() {
        String json = baseWithoutTail(player()) + ",\"questState\":" + questState() + ",\"relationshipState\":" + relationshipState() + "}}";
        assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("abilityState/cooldowns");
    }

    @Test
    @DisplayName("현재 schema v9에서 questState가 누락되면 empty quest로 숨기지 않는다")
    void currentSchemaMissingQuestState_FailsExplicitly() {
        String json = baseWithoutTail(player()) + ",\"abilityState\":{\"cooldowns\":{}},\"relationshipState\":" + relationshipState() + "}}";
        assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("questState/quests/worldFlags/eventFlags");
    }

    @Test
    @DisplayName("현재 schema v9에서 relationshipState가 누락되면 empty 관계로 숨기지 않는다")
    void currentSchemaMissingRelationshipState_FailsExplicitly() {
        String json = baseWithoutTail(player()) + ",\"abilityState\":{\"cooldowns\":{}},\"questState\":" + questState() + "}}";
        assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("relationshipState/relationships");
    }

    private String currentState(String playerField) {
        return baseWithoutTail(playerField) + ",\"abilityState\":{\"cooldowns\":{}},\"questState\":" + questState()
                + ",\"relationshipState\":" + relationshipState() + "}}";
    }

    private String baseWithoutTail(String playerField) {
        return "{\"schemaVersion\":9,\"rulesetVersion\":1,\"state\":{\"turnNumber\":1," + playerField
                + ",\"worldState\":{\"premise\":\"세계관\",\"flags\":{}},\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]},\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}},\"combatEncounter\":null";
    }

    private String player() { return "\"playerCharacter\":{\"description\":\"캐릭터\",\"stats\":" + stats() + ",\"vitals\":" + vitals() + "}"; }
    private String questState() { return "{\"quests\":{},\"worldFlags\":{},\"eventFlags\":{}}"; }
    private String relationshipState() { return "{\"relationships\":{}}"; }
    private String stats() { return "{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10}"; }
    private String vitals() { return "{\"hp\":{\"current\":10,\"max\":10},\"mp\":{\"current\":10,\"max\":10},\"statusEffects\":{}}"; }
}
