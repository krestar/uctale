package com.uctale.uctale.application.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateLegacyUpgradeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameStateUpgrader upgrader = new GameStateUpgrader();

    @Test
    @DisplayName("production raw legacy snapshot은 현재 v9까지 deterministic upgrade된다")
    void productionLegacy_UpgradesToV9() throws Exception {
        String raw;
        try (var input = new ClassPathResource("fixtures/game-state/snapshot-v0-production.json").getInputStream()) {
            raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var upgraded = upgrader.upgrade(objectMapper.readTree(raw));
        assertCurrentBaseShape(upgraded);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("v1 legacy stats 값은 v9까지 보존된다")
    void v1Stats_ArePreserved() throws Exception {
        String json = """
                {"schemaVersion":1,"rulesetVersion":1,"state":{
                  "turnNumber":1,
                  "playerCharacter":{"description":"캐릭터","stats":{"MIGHT":14,"agility":12}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]}
                }}
                """;
        var upgraded = upgrader.upgrade(objectMapper.readTree(json));
        assertCurrentBaseShape(upgraded);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(14);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("agility").asInt()).isEqualTo(12);
    }

    @Test
    @DisplayName("v2부터 v5까지 각 legacy 경계는 현재 v9까지 순차 upgrade된다")
    void legacyVersionChain_UpgradesThroughEveryBoundary() throws Exception {
        for (String json : new String[]{v2(), v3(), v4(), v5()}) assertCurrentBaseShape(upgrader.upgrade(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("legacy schema에 아직 정의되지 않은 후속 필드가 있으면 의미를 추정하지 않는다")
    void legacyFutureFields_FailExplicitly() throws Exception {
        String v2WithInventory = v2().replace(
                "\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]}",
                "\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]},\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(v2WithInventory)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v2").hasMessageContaining("inventory");

        String v3WithVitals = v3().replace(
                "\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10}",
                "\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10},\"vitals\":{\"hp\":{\"current\":1,\"max\":1},\"mp\":{\"current\":1,\"max\":1},\"statusEffects\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(v3WithVitals)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v3").hasMessageContaining("vitals");

        String v4WithCombat = v4().replace("\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}",
                "\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}},\"combatEncounter\":null");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(v4WithCombat)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v4").hasMessageContaining("combatEncounter");
    }

    @Test
    @DisplayName("legacy 능력치 범위 초과는 기본값으로 덮지 않는다")
    void invalidLegacyStat_FailsExplicitly() throws Exception {
        String json = """
                {"schemaVersion":1,"rulesetVersion":1,"state":{
                  "turnNumber":1,"playerCharacter":{"description":"캐릭터","stats":{"MIGHT":999}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]}
                }}
                """;
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("MIGHT");
    }

    private void assertCurrentBaseShape(GameStateUpgrader.UpgradedSnapshot upgraded) {
        assertThat(upgraded.schemaVersion()).isEqualTo(9);
        JsonNode state = upgraded.state();
        assertThat(state.get("inventory").get("items").isEmpty()).isTrue();
        assertThat(state.get("playerCharacter").get("vitals").get("hp").get("current").asInt()).isEqualTo(10);
        assertThat(state.has("combatEncounter")).isTrue();
        assertThat(state.get("combatEncounter").isNull()).isTrue();
        assertThat(state.get("abilityState").get("cooldowns").isEmpty()).isTrue();
        assertThat(state.get("questState").get("quests").isEmpty()).isTrue();
        assertThat(state.get("questState").get("worldFlags").isEmpty()).isTrue();
        assertThat(state.get("questState").get("eventFlags").isEmpty()).isTrue();
        assertThat(state.get("relationshipState").get("relationships").isEmpty()).isTrue();
    }

    private String v2() {
        return """
                {"schemaVersion":2,"rulesetVersion":1,"state":{
                  "turnNumber":1,"playerCharacter":{"description":"캐릭터","stats":{"might":10,"agility":10,"intellect":10,"will":10,"presence":10}},
                  "worldState":{"premise":"세계관","flags":{}},"storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]}
                }}
                """;
    }

    private String v3() {
        return v2().replace("\"schemaVersion\":2", "\"schemaVersion\":3")
                .replace("\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]}",
                        "\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]},\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}");
    }

    private String v4() {
        return v3().replace("\"schemaVersion\":3", "\"schemaVersion\":4")
                .replace("\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10}",
                        "\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10},\"vitals\":{\"hp\":{\"current\":10,\"max\":10},\"mp\":{\"current\":10,\"max\":10},\"statusEffects\":{}}");
    }

    private String v5() {
        return v4().replace("\"schemaVersion\":4", "\"schemaVersion\":5")
                .replace("\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}",
                        "\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}},\"combatEncounter\":null");
    }
}
