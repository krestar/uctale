package com.uctale.uctale.application.game;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GameStateUpgrader {

    public UpgradedSnapshot upgrade(JsonNode snapshotJson) {
        if (snapshotJson == null || !snapshotJson.isObject()) {
            throw new GameStateSnapshotException("GameState snapshot JSON object가 필요합니다.");
        }

        if (!snapshotJson.has("schemaVersion")) {
            return upgradeLegacy(snapshotJson);
        }

        int schemaVersion = requiredInteger(snapshotJson, "schemaVersion");
        if (schemaVersion > GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION) {
            throw new GameStateSnapshotException("지원하지 않는 미래 snapshot schemaVersion입니다: " + schemaVersion);
        }
        if (schemaVersion < 1) {
            throw new GameStateSnapshotException("유효하지 않은 snapshot schemaVersion입니다: " + schemaVersion);
        }

        return switch (schemaVersion) {
            case 1 -> readV1(snapshotJson);
            default -> throw new GameStateSnapshotException("지원하지 않는 snapshot schemaVersion입니다: " + schemaVersion);
        };
    }

    private UpgradedSnapshot upgradeLegacy(JsonNode legacyState) {
        return new UpgradedSnapshot(
                GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION,
                GameStateSnapshotFormat.CURRENT_RULESET_VERSION,
                legacyState
        );
    }

    private UpgradedSnapshot readV1(JsonNode snapshotJson) {
        int rulesetVersion = requiredInteger(snapshotJson, "rulesetVersion");
        if (rulesetVersion != GameStateSnapshotFormat.CURRENT_RULESET_VERSION) {
            throw new GameStateSnapshotException("지원하지 않는 snapshot rulesetVersion입니다: " + rulesetVersion);
        }
        JsonNode state = snapshotJson.get("state");
        if (state == null || !state.isObject()) {
            throw new GameStateSnapshotException("snapshot state가 누락되었거나 손상되었습니다.");
        }
        return new UpgradedSnapshot(
                GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION,
                rulesetVersion,
                state
        );
    }

    private int requiredInteger(JsonNode snapshotJson, String fieldName) {
        JsonNode value = snapshotJson.get(fieldName);
        if (value == null || !value.isIntegralNumber()) {
            throw new GameStateSnapshotException("snapshot " + fieldName + "이 누락되었거나 정수가 아닙니다.");
        }
        return value.asInt();
    }

    public record UpgradedSnapshot(int schemaVersion, int rulesetVersion, JsonNode state) {
    }
}
