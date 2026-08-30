package com.uctale.uctale.application.game;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GameStateUpgrader {

    public UpgradedSnapshot upgrade(JsonNode snapshotJson) {
        VersionedState versionedState = readSourceVersion(snapshotJson);
        while (versionedState.schemaVersion() < GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION) {
            versionedState = upgradeOneVersion(versionedState);
        }
        return new UpgradedSnapshot(
                versionedState.schemaVersion(),
                versionedState.rulesetVersion(),
                versionedState.state()
        );
    }

    private VersionedState readSourceVersion(JsonNode snapshotJson) {
        if (snapshotJson == null || !snapshotJson.isObject()) {
            throw new GameStateSnapshotException("GameState snapshot JSON object가 필요합니다.");
        }

        if (!snapshotJson.has("schemaVersion")) {
            if (!looksLikeLegacyState(snapshotJson)) {
                throw new GameStateSnapshotException("snapshot schemaVersion이 누락되었습니다.");
            }
            return new VersionedState(
                    GameStateSnapshotFormat.LEGACY_SCHEMA_VERSION,
                    GameStateSnapshotFormat.CURRENT_RULESET_VERSION,
                    snapshotJson
            );
        }

        int schemaVersion = requiredInteger(snapshotJson, "schemaVersion");
        if (schemaVersion > GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION) {
            throw new GameStateSnapshotException("지원하지 않는 미래 snapshot schemaVersion입니다: " + schemaVersion);
        }
        if (schemaVersion < 1) {
            throw new GameStateSnapshotException("유효하지 않은 snapshot schemaVersion입니다: " + schemaVersion);
        }

        int rulesetVersion = requiredInteger(snapshotJson, "rulesetVersion");
        if (rulesetVersion != GameStateSnapshotFormat.CURRENT_RULESET_VERSION) {
            throw new GameStateSnapshotException("지원하지 않는 snapshot rulesetVersion입니다: " + rulesetVersion);
        }
        JsonNode state = snapshotJson.get("state");
        if (state == null || !state.isObject()) {
            throw new GameStateSnapshotException("snapshot state가 누락되었거나 손상되었습니다.");
        }
        return new VersionedState(schemaVersion, rulesetVersion, state);
    }

    private VersionedState upgradeOneVersion(VersionedState source) {
        return switch (source.schemaVersion()) {
            case GameStateSnapshotFormat.LEGACY_SCHEMA_VERSION -> upgradeV0ToV1(source);
            default -> throw new GameStateSnapshotException(
                    "snapshot schemaVersion " + source.schemaVersion() + "의 다음 upgrade 경로가 없습니다."
            );
        };
    }

    private VersionedState upgradeV0ToV1(VersionedState legacy) {
        return new VersionedState(
                1,
                legacy.rulesetVersion(),
                legacy.state()
        );
    }

    private boolean looksLikeLegacyState(JsonNode snapshotJson) {
        return snapshotJson.has("turnNumber")
                && snapshotJson.has("playerCharacter")
                && snapshotJson.has("worldState")
                && snapshotJson.has("storyMemory")
                && !snapshotJson.has("rulesetVersion")
                && !snapshotJson.has("state");
    }

    private int requiredInteger(JsonNode snapshotJson, String fieldName) {
        JsonNode value = snapshotJson.get(fieldName);
        if (value == null || !value.isIntegralNumber()) {
            throw new GameStateSnapshotException("snapshot " + fieldName + "이 누락되었거나 정수가 아닙니다.");
        }
        long version = value.asLong();
        if (version < Integer.MIN_VALUE || version > Integer.MAX_VALUE) {
            throw new GameStateSnapshotException("snapshot " + fieldName + "이 지원 범위를 벗어났습니다: " + version);
        }
        return (int) version;
    }

    private record VersionedState(int schemaVersion, int rulesetVersion, JsonNode state) {
    }

    public record UpgradedSnapshot(int schemaVersion, int rulesetVersion, JsonNode state) {
    }
}
