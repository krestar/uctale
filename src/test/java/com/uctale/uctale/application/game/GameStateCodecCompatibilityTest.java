package com.uctale.uctale.application.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateCodecCompatibilityTest {

    private final GameStateCodec codec = new GameStateCodec(new ObjectMapper(), new GameStateUpgrader());

    @Test
    @DisplayName("현재 schema v2에서 stats가 누락된 손상 snapshot은 기본값으로 숨기지 않는다")
    void currentSchemaMissingStats_FailsExplicitly() {
        String json = """
                {
                  "schemaVersion": 2,
                  "rulesetVersion": 1,
                  "state": {
                    "turnNumber": 1,
                    "playerCharacter": {"description": "캐릭터"},
                    "worldState": {"premise": "세계관", "flags": {}},
                    "storyMemory": {"canonicalFacts": [], "rollingSummary": "", "recentTurns": []}
                  }
                }
                """;

        assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("역직렬화");
    }
}
