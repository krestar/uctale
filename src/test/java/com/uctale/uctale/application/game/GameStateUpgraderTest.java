package com.uctale.uctale.application.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateUpgraderTest {

    private static final String LEGACY_FIXTURE = "fixtures/game-state/snapshot-v0-production.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameStateUpgrader upgrader = new GameStateUpgrader();

    @Test
    @DisplayName("production legacy raw GameState를 schema v2와 기본 능력치로 순수 upgrade한다")
    void legacyRawState_IsUpgradedToCurrentSchema() throws Exception {
        JsonNode legacy = objectMapper.readTree(legacyStateJson());

        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(legacy);

        assertThat(upgraded.schemaVersion()).isEqualTo(2);
        assertThat(upgraded.rulesetVersion()).isEqualTo(1);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(10);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("presence").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("schema v1 legacy stats의 canonical 키는 v2 typed stats로 보존한다")
    void schemaV1Stats_AreNormalizedWithoutLosingCanonicalValues() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "rulesetVersion": 1,
                  "state": {
                    "turnNumber": 1,
                    "playerCharacter": {
                      "description": "캐릭터",
                      "stats": {"MIGHT": 14, "agility": 12}
                    },
                    "worldState": {"premise": "세계관", "flags": {}},
                    "storyMemory": {
                      "canonicalFacts": [],
                      "rollingSummary": "",
                      "recentTurns": []
                    }
                  }
                }
                """);

        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        JsonNode stats = upgraded.state().get("playerCharacter").get("stats");

        assertThat(upgraded.schemaVersion()).isEqualTo(2);
        assertThat(stats.get("might").asInt()).isEqualTo(14);
        assertThat(stats.get("agility").asInt()).isEqualTo(12);
        assertThat(stats.get("intellect").asInt()).isEqualTo(10);
        assertThat(stats.get("will").asInt()).isEqualTo(10);
        assertThat(stats.get("presence").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("schema v2 snapshot은 state를 그대로 읽는다")
    void schemaV2_IsReadWithoutMutation() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "rulesetVersion": 1,
                  "state": {
                    "turnNumber": 1,
                    "playerCharacter": {
                      "description": "캐릭터",
                      "stats": {"might":10,"agility":10,"intellect":10,"will":10,"presence":10}
                    },
                    "worldState": {"premise": "세계관", "flags": {}},
                    "storyMemory": {"canonicalFacts": [], "rollingSummary": "", "recentTurns": []}
                  }
                }
                """);

        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);

        assertThat(upgraded.schemaVersion()).isEqualTo(2);
        assertThat(upgraded.rulesetVersion()).isEqualTo(1);
        assertThat(upgraded.state()).isEqualTo(snapshot.get("state"));
    }

    @Test
    @DisplayName("legacy 능력치가 허용 범위를 벗어나면 추정하지 않고 실패한다")
    void invalidLegacyStat_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "rulesetVersion": 1,
                  "state": {
                    "turnNumber": 1,
                    "playerCharacter": {"description": "캐릭터", "stats": {"MIGHT": 999}},
                    "worldState": {"premise": "세계관", "flags": {}},
                    "storyMemory": {"canonicalFacts": [], "rollingSummary": "", "recentTurns": []}
                  }
                }
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("MIGHT");
    }

    @Test
    @DisplayName("미래 schema version은 기본값 처리하지 않고 실패한다")
    void futureSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion": 3, "rulesetVersion": 1, "state": {}}
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("미래 snapshot schemaVersion");
    }

    @Test
    @DisplayName("미지원 ruleset version은 자동 재판정하지 않고 실패한다")
    void unsupportedRulesetVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion": 2, "rulesetVersion": 2, "state": {}}
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
                {"schemaVersion": 2, "rulesetVersion": 1}
                """);

        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("state가 누락");
    }

    private String legacyStateJson() throws Exception {
        ClassPathResource resource = new ClassPathResource(LEGACY_FIXTURE);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
