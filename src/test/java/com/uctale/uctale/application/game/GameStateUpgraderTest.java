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
    @DisplayName("production legacy raw GameState를 schema v1로 순수 upgrade한다")
    void legacyRawState_IsUpgradedToCurrentSchema() throws Exception {
        JsonNode legacy = objectMapper.readTree(legacyStateJson());

        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(legacy);

        assertThat(upgraded.schemaVersion()).isEqualTo(1);
        assertThat(upgraded.rulesetVersion()).isEqualTo(1);
        assertThat(upgraded.state()).isEqualTo(legacy);
    }

    @Test
    @DisplayName("schema v1 snapshot은 state를 그대로 읽는다")
    void schemaV1_IsReadWithoutMutation() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "rulesetVersion": 1,
                  "state": %s
                }
                """.formatted(legacyStateJson()));

        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);

        assertThat(upgraded.schemaVersion()).isEqualTo(1);
        assertThat(upgraded.rulesetVersion()).isEqualTo(1);
        assertThat(upgraded.state().get("turnNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("미래 schema version은 기본값 처리하지 않고 실패한다")
    void futureSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion": 2, "rulesetVersion": 1, "state": {}}
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("미래 snapshot schemaVersion");
    }

    @Test
    @DisplayName("미지원 ruleset version은 자동 재판정하지 않고 실패한다")
    void unsupportedRulesetVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion": 1, "rulesetVersion": 2, "state": {}}
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("rulesetVersion");
    }

    @Test
    @DisplayName("envelope에서 schemaVersion만 누락된 손상 snapshot은 legacy로 오인하지 않는다")
    void damagedEnvelopeMissingSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"rulesetVersion": 1, "state": {}}
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("schemaVersion이 누락");
    }

    @Test
    @DisplayName("schemaVersion이 있어도 state가 누락되면 손상 snapshot으로 실패한다")
    void missingState_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion": 1, "rulesetVersion": 1}
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("state가 누락");
    }

    private String legacyStateJson() {
        return """
                {
                  "turnNumber": 1,
                  "playerCharacter": {"description": "캐릭터", "stats": {}},
                  "worldState": {"premise": "세계관", "flags": {}},
                  "storyMemory": {
                    "canonicalFacts": [
                      {"key": "world.premise", "value": "세계관"},
                      {"key": "player.description", "value": "캐릭터"}
                    ],
                    "rollingSummary": "",
                    "recentTurns": [
                      {"turnNumber": 1, "playerAction": "", "storyText": "첫 이야기"}
                    ]
                  }
                }
                """;
    }
}
