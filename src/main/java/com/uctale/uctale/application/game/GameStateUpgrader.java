package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.CharacterStats;
import com.uctale.uctale.domain.game.CharacterVitals;
import com.uctale.uctale.domain.game.EnemyCombatProfile;
import com.uctale.uctale.domain.game.ItemCombatModifiers;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GameStateUpgrader {

    public UpgradedSnapshot upgrade(JsonNode snapshotJson) {
        VersionedState versionedState = readSourceVersion(snapshotJson);
        while (versionedState.schemaVersion() < GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION) {
            versionedState = upgradeOneVersion(versionedState);
        }
        validateCurrentShape(versionedState.state());
        return new UpgradedSnapshot(versionedState.schemaVersion(), versionedState.rulesetVersion(), versionedState.state());
    }

    private VersionedState readSourceVersion(JsonNode snapshotJson) {
        if (snapshotJson == null || !snapshotJson.isObject()) throw new GameStateSnapshotException("GameState snapshot JSON object가 필요합니다.");
        if (!snapshotJson.has("schemaVersion")) {
            if (!looksLikeLegacyState(snapshotJson)) throw new GameStateSnapshotException("snapshot schemaVersion이 누락되었습니다.");
            return new VersionedState(GameStateSnapshotFormat.LEGACY_SCHEMA_VERSION, GameStateSnapshotFormat.LEGACY_RULESET_VERSION, snapshotJson);
        }
        int schemaVersion = requiredInteger(snapshotJson, "schemaVersion");
        if (schemaVersion > GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION) throw new GameStateSnapshotException("지원하지 않는 미래 snapshot schemaVersion입니다: " + schemaVersion);
        if (schemaVersion < 1) throw new GameStateSnapshotException("유효하지 않은 snapshot schemaVersion입니다: " + schemaVersion);
        int rulesetVersion = requiredInteger(snapshotJson, "rulesetVersion");
        if (rulesetVersion != GameStateSnapshotFormat.CURRENT_RULESET_VERSION) throw new GameStateSnapshotException("지원하지 않는 snapshot rulesetVersion입니다: " + rulesetVersion);
        JsonNode state = snapshotJson.get("state");
        if (state == null || !state.isObject()) throw new GameStateSnapshotException("snapshot state가 누락되었거나 손상되었습니다.");
        return new VersionedState(schemaVersion, rulesetVersion, state);
    }

    private VersionedState upgradeOneVersion(VersionedState source) {
        return switch (source.schemaVersion()) {
            case GameStateSnapshotFormat.LEGACY_SCHEMA_VERSION -> upgradeV0ToV1(source);
            case 1 -> upgradeV1ToV2(source);
            case 2 -> upgradeV2ToV3(source);
            case 3 -> upgradeV3ToV4(source);
            case 4 -> upgradeV4ToV5(source);
            case 5 -> upgradeV5ToV6(source);
            case 6 -> upgradeV6ToV7(source);
            case 7 -> upgradeV7ToV8(source);
            case 8 -> upgradeV8ToV9(source);
            default -> throw new GameStateSnapshotException("snapshot schemaVersion " + source.schemaVersion() + "의 다음 upgrade 경로가 없습니다.");
        };
    }

    private VersionedState upgradeV0ToV1(VersionedState legacy) {
        return new VersionedState(1, legacy.rulesetVersion(), legacy.state());
    }

    private VersionedState upgradeV1ToV2(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        JsonNode playerNode = state.get("playerCharacter");
        if (!(playerNode instanceof ObjectNode playerCharacter)) throw new GameStateSnapshotException("snapshot playerCharacter가 누락되었거나 손상되었습니다.");
        JsonNode legacyStats = playerCharacter.get("stats");
        if (legacyStats != null && !legacyStats.isObject()) throw new GameStateSnapshotException("snapshot playerCharacter.stats가 object가 아닙니다.");
        ObjectNode normalizedStats = JsonNodeFactory.instance.objectNode();
        normalizedStats.put("might", legacyScore(legacyStats, "MIGHT", "might"));
        normalizedStats.put("agility", legacyScore(legacyStats, "AGILITY", "agility"));
        normalizedStats.put("intellect", legacyScore(legacyStats, "INTELLECT", "intellect"));
        normalizedStats.put("will", legacyScore(legacyStats, "WILL", "will"));
        normalizedStats.put("presence", legacyScore(legacyStats, "PRESENCE", "presence"));
        playerCharacter.set("stats", normalizedStats);
        return new VersionedState(2, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV2ToV3(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        if (state.has("inventory")) throw new GameStateSnapshotException("schema v2 snapshot에는 inventory 필드가 정의되어 있지 않습니다.");
        ObjectNode emptyInventory = JsonNodeFactory.instance.objectNode();
        emptyInventory.set("items", JsonNodeFactory.instance.objectNode());
        ObjectNode equipment = JsonNodeFactory.instance.objectNode();
        equipment.set("slots", JsonNodeFactory.instance.objectNode());
        emptyInventory.set("equipment", equipment);
        state.set("inventory", emptyInventory);
        return new VersionedState(3, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV3ToV4(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        JsonNode playerNode = state.get("playerCharacter");
        if (!(playerNode instanceof ObjectNode playerCharacter)) throw new GameStateSnapshotException("snapshot playerCharacter가 누락되었거나 손상되었습니다.");
        if (playerCharacter.has("vitals")) throw new GameStateSnapshotException("schema v3 snapshot에는 playerCharacter.vitals 필드가 정의되어 있지 않습니다.");
        ObjectNode vitals = JsonNodeFactory.instance.objectNode();
        vitals.set("hp", fullPool(CharacterVitals.DEFAULT_MAX_HP));
        vitals.set("mp", fullPool(CharacterVitals.DEFAULT_MAX_MP));
        vitals.set("statusEffects", JsonNodeFactory.instance.objectNode());
        playerCharacter.set("vitals", vitals);
        return new VersionedState(4, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV4ToV5(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        if (state.has("combatEncounter")) throw new GameStateSnapshotException("schema v4 snapshot에는 combatEncounter 필드가 정의되어 있지 않습니다.");
        state.set("combatEncounter", JsonNodeFactory.instance.nullNode());
        return new VersionedState(5, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV5ToV6(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        JsonNode inventoryNode = state.get("inventory");
        if (!(inventoryNode instanceof ObjectNode inventory)) throw new GameStateSnapshotException("schema v5 snapshot inventory가 누락되었거나 손상되었습니다.");
        JsonNode itemsNode = inventory.get("items");
        if (!(itemsNode instanceof ObjectNode items)) throw new GameStateSnapshotException("schema v5 snapshot inventory.items가 누락되었거나 손상되었습니다.");
        for (var entry : items.properties()) {
            if (!(entry.getValue() instanceof ObjectNode item)) throw new GameStateSnapshotException("schema v5 snapshot inventory item이 object가 아닙니다.");
            JsonNode definitionNode = item.get("definition");
            if (!(definitionNode instanceof ObjectNode definition)) throw new GameStateSnapshotException("schema v5 snapshot item definition이 누락되었거나 손상되었습니다.");
            if (definition.has("combatModifiers")) throw new GameStateSnapshotException("schema v5 snapshot에는 item combatModifiers가 정의되어 있지 않습니다.");
            ObjectNode modifiers = JsonNodeFactory.instance.objectNode();
            modifiers.put("attackBonus", ItemCombatModifiers.none().attackBonus());
            modifiers.put("damageBonus", ItemCombatModifiers.none().damageBonus());
            definition.set("combatModifiers", modifiers);
        }

        JsonNode combatNode = state.get("combatEncounter");
        if (combatNode == null) throw new GameStateSnapshotException("schema v5 snapshot combatEncounter 필드가 누락되었습니다.");
        if (!combatNode.isNull()) {
            if (!(combatNode instanceof ObjectNode combat)) throw new GameStateSnapshotException("schema v5 snapshot combatEncounter가 object가 아닙니다.");
            JsonNode enemiesNode = combat.get("enemies");
            if (!(enemiesNode instanceof ObjectNode enemies)) throw new GameStateSnapshotException("schema v5 snapshot combatEncounter.enemies가 누락되었거나 손상되었습니다.");
            for (var entry : enemies.properties()) {
                if (!(entry.getValue() instanceof ObjectNode enemy)) throw new GameStateSnapshotException("schema v5 snapshot enemy가 object가 아닙니다.");
                if (enemy.has("combatProfile")) throw new GameStateSnapshotException("schema v5 snapshot에는 enemy combatProfile이 정의되어 있지 않습니다.");
                ObjectNode profile = JsonNodeFactory.instance.objectNode();
                profile.put("defenseScore", EnemyCombatProfile.DEFAULT_DEFENSE_SCORE);
                profile.put("damageReduction", EnemyCombatProfile.DEFAULT_DAMAGE_REDUCTION);
                enemy.set("combatProfile", profile);
            }
        }
        return new VersionedState(6, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV6ToV7(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        if (state.has("abilityState")) throw new GameStateSnapshotException("schema v6 snapshot에는 abilityState 필드가 정의되어 있지 않습니다.");
        ObjectNode abilityState = JsonNodeFactory.instance.objectNode();
        abilityState.set("cooldowns", JsonNodeFactory.instance.objectNode());
        state.set("abilityState", abilityState);
        return new VersionedState(7, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV7ToV8(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        if (state.has("questState")) throw new GameStateSnapshotException("schema v7 snapshot에는 questState 필드가 정의되어 있지 않습니다.");
        ObjectNode questState = JsonNodeFactory.instance.objectNode();
        questState.set("quests", JsonNodeFactory.instance.objectNode());
        questState.set("worldFlags", JsonNodeFactory.instance.objectNode());
        questState.set("eventFlags", JsonNodeFactory.instance.objectNode());
        state.set("questState", questState);
        return new VersionedState(8, source.rulesetVersion(), state);
    }

    private VersionedState upgradeV8ToV9(VersionedState source) {
        ObjectNode state = objectCopy(source.state());
        if (state.has("relationshipState")) throw new GameStateSnapshotException("schema v8 snapshot에는 relationshipState 필드가 정의되어 있지 않습니다.");
        ObjectNode relationshipState = JsonNodeFactory.instance.objectNode();
        relationshipState.set("relationships", JsonNodeFactory.instance.objectNode());
        state.set("relationshipState", relationshipState);
        return new VersionedState(9, source.rulesetVersion(), state);
    }

    private void validateCurrentShape(JsonNode state) {
        if (!state.has("combatEncounter")) throw new GameStateSnapshotException("현재 schema snapshot combatEncounter 필드가 누락되었습니다.");
        JsonNode inventoryNode = state.get("inventory");
        if (!(inventoryNode instanceof ObjectNode inventory) || !(inventory.get("items") instanceof ObjectNode items)) {
            throw new GameStateSnapshotException("현재 schema snapshot inventory/items가 누락되었거나 손상되었습니다.");
        }
        for (var entry : items.properties()) {
            JsonNode definition = entry.getValue().get("definition");
            if (definition == null || !definition.isObject() || !definition.has("combatModifiers")) throw new GameStateSnapshotException("현재 schema snapshot item combatModifiers가 누락되었습니다.");
        }
        JsonNode combatNode = state.get("combatEncounter");
        if (combatNode != null && !combatNode.isNull()) {
            JsonNode enemies = combatNode.get("enemies");
            if (enemies == null || !enemies.isObject()) throw new GameStateSnapshotException("현재 schema snapshot combat enemies가 누락되었거나 손상되었습니다.");
            for (var entry : enemies.properties()) {
                JsonNode enemy = entry.getValue();
                if (enemy == null || !enemy.isObject() || !enemy.has("combatProfile")) throw new GameStateSnapshotException("현재 schema snapshot enemy combatProfile이 누락되었습니다.");
            }
        }
        JsonNode abilityNode = state.get("abilityState");
        if (!(abilityNode instanceof ObjectNode abilityState) || !(abilityState.get("cooldowns") instanceof ObjectNode cooldowns)) {
            throw new GameStateSnapshotException("현재 schema snapshot abilityState/cooldowns가 누락되었거나 손상되었습니다.");
        }
        for (var entry : cooldowns.properties()) {
            JsonNode remaining = entry.getValue();
            if (entry.getKey() == null || entry.getKey().isBlank() || remaining == null || !remaining.isIntegralNumber()
                    || remaining.asLong() < 1 || remaining.asLong() > Integer.MAX_VALUE) {
                throw new GameStateSnapshotException("현재 schema snapshot ability cooldown이 손상되었습니다: " + entry.getKey());
            }
        }
        JsonNode questNode = state.get("questState");
        if (!(questNode instanceof ObjectNode questState)
                || !(questState.get("quests") instanceof ObjectNode)
                || !(questState.get("worldFlags") instanceof ObjectNode)
                || !(questState.get("eventFlags") instanceof ObjectNode)) {
            throw new GameStateSnapshotException("현재 schema snapshot questState/quests/worldFlags/eventFlags가 누락되었거나 손상되었습니다.");
        }
        JsonNode relationshipNode = state.get("relationshipState");
        if (!(relationshipNode instanceof ObjectNode relationshipState)
                || !(relationshipState.get("relationships") instanceof ObjectNode)) {
            throw new GameStateSnapshotException("현재 schema snapshot relationshipState/relationships가 누락되었거나 손상되었습니다.");
        }
    }

    private ObjectNode objectCopy(JsonNode state) {
        if (!(state.deepCopy() instanceof ObjectNode copy)) throw new GameStateSnapshotException("snapshot state가 object가 아닙니다.");
        return copy;
    }

    private ObjectNode fullPool(int max) {
        ObjectNode pool = JsonNodeFactory.instance.objectNode();
        pool.put("current", max);
        pool.put("max", max);
        return pool;
    }

    private int legacyScore(JsonNode stats, String enumKey, String fieldKey) {
        if (stats == null) return CharacterStats.DEFAULT_SCORE;
        JsonNode value = stats.get(fieldKey);
        if (value == null) value = stats.get(enumKey);
        if (value == null) return CharacterStats.DEFAULT_SCORE;
        if (!value.isIntegralNumber()) throw new GameStateSnapshotException("snapshot 능력치 " + enumKey + "가 정수가 아닙니다.");
        long score = value.asLong();
        if (score < CharacterStats.MIN_SCORE || score > CharacterStats.MAX_SCORE) throw new GameStateSnapshotException("snapshot 능력치 " + enumKey + "가 허용 범위를 벗어났습니다: " + score);
        return (int) score;
    }

    private boolean looksLikeLegacyState(JsonNode snapshotJson) {
        return snapshotJson.has("turnNumber") && snapshotJson.has("playerCharacter") && snapshotJson.has("worldState")
                && snapshotJson.has("storyMemory") && !snapshotJson.has("rulesetVersion") && !snapshotJson.has("state");
    }

    private int requiredInteger(JsonNode snapshotJson, String fieldName) {
        JsonNode value = snapshotJson.get(fieldName);
        if (value == null || !value.isIntegralNumber()) throw new GameStateSnapshotException("snapshot " + fieldName + "이 누락되었거나 정수가 아닙니다.");
        long version = value.asLong();
        if (version < Integer.MIN_VALUE || version > Integer.MAX_VALUE) throw new GameStateSnapshotException("snapshot " + fieldName + "이 지원 범위를 벗어났습니다: " + version);
        return (int) version;
    }

    private record VersionedState(int schemaVersion, int rulesetVersion, JsonNode state) {}
    public record UpgradedSnapshot(int schemaVersion, int rulesetVersion, JsonNode state) {}
}
