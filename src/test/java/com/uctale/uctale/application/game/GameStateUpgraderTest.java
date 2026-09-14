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
    @DisplayName("schema v6 snapshot은 ability와 quest 빈 상태를 순차 추가해 v8로 승격한다")
    void schemaV6_AddsEmptyAbilityAndQuestState() throws Exception {
        var upgraded = upgrader.upgrade(objectMapper.readTree(v6State()));
        assertThat(upgraded.schemaVersion()).isEqualTo(8);
        JsonNode state = upgraded.state();
        assertThat(state.get("abilityState").get("cooldowns").isEmpty()).isTrue();
        assertThat(state.get("questState").get("quests").isEmpty()).isTrue();
        assertThat(state.get("questState").get("worldFlags").isEmpty()).isTrue();
        assertThat(state.get("questState").get("eventFlags").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("schema v7 snapshot은 과거 의미를 추측하지 않고 빈 quest state만 추가한다")
    void schemaV7_AddsEmptyQuestState() throws Exception {
        var upgraded = upgrader.upgrade(objectMapper.readTree(v7State("{}")));
        assertThat(upgraded.schemaVersion()).isEqualTo(8);
        assertThat(upgraded.state().get("questState").get("quests").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("schema v7에 questState가 이미 있으면 정의되지 않은 의미를 임의 승격하지 않는다")
    void schemaV7WithQuestState_FailsExplicitly() throws Exception {
        String json = v7State("{}").replace("\"abilityState\":{\"cooldowns\":{}}",
                "\"abilityState\":{\"cooldowns\":{}},\"questState\":{\"quests\":{},\"worldFlags\":{},\"eventFlags\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v7").hasMessageContaining("questState");
    }

    @Test
    @DisplayName("현재 v8 questState 누락은 조용히 empty로 복구하지 않는다")
    void schemaV8MissingQuestState_FailsExplicitly() throws Exception {
        String json = v7State("{}").replace("\"schemaVersion\":7", "\"schemaVersion\":8");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("questState/quests/worldFlags/eventFlags");
    }

    @Test
    @DisplayName("현재 v8 cooldown 값이 0이나 비정수면 손상 snapshot으로 거절한다")
    void schemaV8InvalidCooldown_FailsExplicitly() throws Exception {
        String zero = currentV8("0");
        String text = currentV8("\"2\"");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(zero)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("cooldown");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(text)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("cooldown");
    }

    @Test
    @DisplayName("미래 schema와 미지원 ruleset은 명시적으로 실패한다")
    void unsupportedVersions_FailExplicitly() throws Exception {
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree("{\"schemaVersion\":9,\"rulesetVersion\":1,\"state\":{}}")))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("미래 snapshot schemaVersion");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree("{\"schemaVersion\":8,\"rulesetVersion\":2,\"state\":{}}")))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
    }

    private String currentV8(String cooldownValue) {
        return v7State("{\"arcane-bolt\":" + cooldownValue + "}")
                .replace("\"schemaVersion\":7", "\"schemaVersion\":8")
                .replace("\"abilityState\":{\"cooldowns\":{\"arcane-bolt\":" + cooldownValue + "}}",
                        "\"abilityState\":{\"cooldowns\":{\"arcane-bolt\":" + cooldownValue + "}},\"questState\":{\"quests\":{},\"worldFlags\":{},\"eventFlags\":{}}");
    }

    private String v7State(String cooldowns) {
        return v6State().replace("\"schemaVersion\":6", "\"schemaVersion\":7")
                .replace("\"combatEncounter\":null", "\"combatEncounter\":null,\"abilityState\":{\"cooldowns\":" + cooldowns + "}");
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
