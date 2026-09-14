package com.uctale.uctale.application.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateUpgraderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameStateUpgrader upgrader = new GameStateUpgrader();

    @Test
    @DisplayName("schema v6 snapshot은 deterministic empty ability state를 추가해 v7로 승격한다")
    void schemaV6_AddsEmptyAbilityState() throws Exception {
        JsonNode upgraded = upgrader.upgrade(objectMapper.readTree(v6State())).state();
        assertThat(upgrader.upgrade(objectMapper.readTree(v6State())).schemaVersion()).isEqualTo(7);
        assertThat(upgraded.get("abilityState").get("cooldowns").isEmpty()).isTrue();
        assertThat(upgraded.get("combatEncounter").isNull()).isTrue();
    }

    @Test
    @DisplayName("schema v6에 abilityState가 이미 있으면 정의되지 않은 의미를 임의 승격하지 않는다")
    void schemaV6WithAbilityState_FailsExplicitly() throws Exception {
        String json = v6State().replace("\"combatEncounter\":null", "\"combatEncounter\":null,\"abilityState\":{\"cooldowns\":{\"arcane-bolt\":2}} ");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v6").hasMessageContaining("abilityState");
    }

    @Test
    @DisplayName("현재 v7 cooldown 값이 0이나 비정수면 손상 snapshot으로 거절한다")
    void schemaV7InvalidCooldown_FailsExplicitly() throws Exception {
        String zero = v7State("0");
        String text = v7State("\"2\"");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(zero)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("cooldown");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(text)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("cooldown");
    }

    @Test
    @DisplayName("현재 v7 abilityState 누락은 조용히 empty로 복구하지 않는다")
    void schemaV7MissingAbilityState_FailsExplicitly() throws Exception {
        String currentWithoutAbility = v6State().replace("\"schemaVersion\":6", "\"schemaVersion\":7");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(currentWithoutAbility)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("abilityState/cooldowns");
    }

    @Test
    @DisplayName("미래 schema와 미지원 ruleset은 명시적으로 실패한다")
    void unsupportedVersions_FailExplicitly() throws Exception {
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree("{\"schemaVersion\":8,\"rulesetVersion\":1,\"state\":{}}")))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("미래 snapshot schemaVersion");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree("{\"schemaVersion\":7,\"rulesetVersion\":2,\"state\":{}}")))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
    }

    private String v7State(String cooldownValue) {
        return v6State().replace("\"schemaVersion\":6", "\"schemaVersion\":7")
                .replace("\"combatEncounter\":null", "\"combatEncounter\":null,\"abilityState\":{\"cooldowns\":{\"arcane-bolt\":" + cooldownValue + "}}");
    }

    private String v6State() {
        return """
                {"schemaVersion":6,"rulesetVersion":1,"state":{
                  "turnNumber":1,
                  "playerCharacter":{"description":"캐릭터","stats":{"might":10,"agility":10,"intellect":10,"will":10,"presence":10},"vitals":{"hp":{"current":10,"max":10},"mp":{"current":10,"max":10},"statusEffects":{}}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]},
                  "inventory":{"items":{},"equipment":{"slots":{}}},
                  "combatEncounter":null
                }}
                """;
    }
}
