package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.CharacterVitals;
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
    @DisplayName("production legacy raw GameState를 schema v6 기본 stats/inventory/vitals/no-combat으로 순수 upgrade한다")
    void legacyRawState_IsUpgradedToCurrentSchema() throws Exception {
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(objectMapper.readTree(legacyStateJson()));
        assertThat(upgraded.schemaVersion()).isEqualTo(6);
        assertThat(upgraded.rulesetVersion()).isEqualTo(1);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(10);
        assertThat(upgraded.state().get("inventory").get("items").isEmpty()).isTrue();
        assertDefaultVitals(upgraded.state());
        assertThat(upgraded.state().has("combatEncounter")).isTrue();
        assertThat(upgraded.state().get("combatEncounter").isNull()).isTrue();
    }

    @Test
    @DisplayName("schema v1 legacy stats의 canonical 값은 v6까지 보존한다")
    void schemaV1Stats_AreNormalizedWithoutLosingCanonicalValues() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion":1,"rulesetVersion":1,"state":{
                  "turnNumber":1,
                  "playerCharacter":{"description":"캐릭터","stats":{"MIGHT":14,"agility":12}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]}
                }}
                """);
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        assertThat(upgraded.schemaVersion()).isEqualTo(6);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(14);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("agility").asInt()).isEqualTo(12);
        assertDefaultVitals(upgraded.state());
        assertThat(upgraded.state().get("combatEncounter").isNull()).isTrue();
    }

    @Test
    @DisplayName("schema v2는 inventory, vitals, no-combat와 전투 modifier를 순차적으로 추가한다")
    void schemaV2_IsUpgradedThroughV6() throws Exception {
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(objectMapper.readTree(v2State()));
        assertThat(upgraded.schemaVersion()).isEqualTo(6);
        assertThat(upgraded.state().get("inventory").get("items").isEmpty()).isTrue();
        assertDefaultVitals(upgraded.state());
        assertThat(upgraded.state().get("combatEncounter").isNull()).isTrue();
    }

    @Test
    @DisplayName("schema v3는 기존 state 의미를 보존하고 현재 schema까지 승격한다")
    void schemaV3_UpgradesToV6() throws Exception {
        JsonNode snapshot = objectMapper.readTree(v3State());
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        assertThat(upgraded.schemaVersion()).isEqualTo(6);
        assertThat(upgraded.state().get("inventory")).isEqualTo(snapshot.get("state").get("inventory"));
        assertDefaultVitals(upgraded.state());
        assertThat(upgraded.state().get("combatEncounter").isNull()).isTrue();
    }

    @Test
    @DisplayName("schema v4는 기존 state 의미를 보존하고 현재 schema까지 승격한다")
    void schemaV4_UpgradesToV6() throws Exception {
        JsonNode snapshot = objectMapper.readTree(v4State());
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        assertThat(upgraded.schemaVersion()).isEqualTo(6);
        assertThat(upgraded.state().get("playerCharacter")).isEqualTo(snapshot.get("state").get("playerCharacter"));
        assertThat(upgraded.state().get("combatEncounter").isNull()).isTrue();
    }

    @Test
    @DisplayName("schema v5 item과 enemy에는 결정적인 기본 combat 수치를 추가한다")
    void schemaV5_AddsCombatDefaults() throws Exception {
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(objectMapper.readTree(v5CombatState()));
        assertThat(upgraded.schemaVersion()).isEqualTo(6);

        JsonNode definition = upgraded.state().get("inventory").get("items").get("blade").get("definition");
        assertThat(definition.get("combatModifiers").get("attackBonus").asInt()).isZero();
        assertThat(definition.get("combatModifiers").get("damageBonus").asInt()).isZero();

        JsonNode enemy = upgraded.state().get("combatEncounter").get("enemies").get("wolf");
        assertThat(enemy.get("combatProfile").get("defenseScore").asInt()).isEqualTo(10);
        assertThat(enemy.get("combatProfile").get("damageReduction").asInt()).isZero();
    }

    @Test
    @DisplayName("schema v6 snapshot은 state를 그대로 읽는다")
    void schemaV6_IsReadWithoutMutation() throws Exception {
        GameStateUpgrader.UpgradedSnapshot v6 = upgrader.upgrade(objectMapper.readTree(v5State()));
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":6,\"rulesetVersion\":1,\"state\":" + v6.state() + "}");
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        assertThat(upgraded.schemaVersion()).isEqualTo(6);
        assertThat(upgraded.state()).isEqualTo(snapshot.get("state"));
    }

    @Test
    @DisplayName("legacy 능력치가 허용 범위를 벗어나면 추정하지 않고 실패한다")
    void invalidLegacyStat_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                {"schemaVersion":1,"rulesetVersion":1,"state":{
                  "turnNumber":1,"playerCharacter":{"description":"캐릭터","stats":{"MIGHT":999}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]}
                }}
                """);
        assertThatThrownBy(() -> upgrader.upgrade(snapshot)).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("MIGHT");
    }

    @Test
    @DisplayName("schema v2에 inventory가 있으면 정의되지 않은 의미를 추정하지 않는다")
    void schemaV2WithInventory_FailsExplicitly() throws Exception {
        String json = v2State().replace("\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]}",
                "\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]},\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json))).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v2").hasMessageContaining("inventory");
    }

    @Test
    @DisplayName("schema v3에 vitals가 이미 있으면 정의되지 않은 의미를 승격하지 않는다")
    void schemaV3WithVitals_FailsExplicitly() throws Exception {
        String json = v3State().replace("\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10}",
                "\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10},\"vitals\":{\"hp\":{\"current\":1,\"max\":1},\"mp\":{\"current\":1,\"max\":1},\"statusEffects\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json))).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v3").hasMessageContaining("vitals");
    }

    @Test
    @DisplayName("schema v4에 combatEncounter가 이미 있으면 정의되지 않은 의미를 canonical v5로 승격하지 않는다")
    void schemaV4WithCombat_FailsExplicitly() throws Exception {
        String json = v4State().replace("\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}",
                "\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}},\"combatEncounter\":null");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json))).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v4").hasMessageContaining("combatEncounter");
    }

    @Test
    @DisplayName("schema v5에 이미 combatProfile이 있으면 v6 의미로 임의 승격하지 않는다")
    void schemaV5WithCombatProfile_FailsExplicitly() throws Exception {
        String json = v5CombatState().replace("\"displayName\":\"늑대\",",
                "\"displayName\":\"늑대\",\"combatProfile\":{\"defenseScore\":99,\"damageReduction\":0},");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("schema v5")
                .hasMessageContaining("combatProfile");
    }

    @Test
    @DisplayName("schema v5에서 combatEncounter 필드가 누락되면 no-combat로 조용히 기본값 처리하지 않는다")
    void schemaV5MissingCombatField_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree(v4State().replace("\"schemaVersion\":4", "\"schemaVersion\":5"));
        assertThatThrownBy(() -> upgrader.upgrade(snapshot)).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("combatEncounter 필드가 누락");
    }

    @Test
    @DisplayName("현재 schema v6에서 item combatModifiers가 누락되면 조용히 기본값 처리하지 않는다")
    void schemaV6MissingItemCombatModifiers_FailsExplicitly() throws Exception {
        String json = v5CombatState().replace("\"schemaVersion\":5", "\"schemaVersion\":6");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("combatModifiers");
    }

    @Test
    @DisplayName("미래 schema version은 기본값 처리하지 않고 실패한다")
    void futureSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":7,\"rulesetVersion\":1,\"state\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot)).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("미래 snapshot schemaVersion");
    }

    @Test
    @DisplayName("미지원 ruleset version은 자동 재판정하지 않고 실패한다")
    void unsupportedRulesetVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":6,\"rulesetVersion\":2,\"state\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot)).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
    }

    @Test
    @DisplayName("envelope에서 schemaVersion만 누락된 손상 snapshot은 legacy로 오인하지 않는다")
    void damagedEnvelopeMissingSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"rulesetVersion\":1,\"state\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot)).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schemaVersion이 누락");
    }

    @Test
    @DisplayName("schemaVersion이 있어도 state가 누락되면 손상 snapshot으로 실패한다")
    void missingState_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":6,\"rulesetVersion\":1}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot)).isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("state가 누락");
    }

    private void assertDefaultVitals(JsonNode state) {
        JsonNode vitals = state.get("playerCharacter").get("vitals");
        assertThat(vitals.get("hp").get("current").asInt()).isEqualTo(CharacterVitals.DEFAULT_MAX_HP);
        assertThat(vitals.get("hp").get("max").asInt()).isEqualTo(CharacterVitals.DEFAULT_MAX_HP);
        assertThat(vitals.get("mp").get("current").asInt()).isEqualTo(CharacterVitals.DEFAULT_MAX_MP);
        assertThat(vitals.get("statusEffects").isEmpty()).isTrue();
    }

    private String v2State() {
        return """
                {"schemaVersion":2,"rulesetVersion":1,"state":{
                  "turnNumber":1,
                  "playerCharacter":{"description":"캐릭터","stats":{"might":10,"agility":10,"intellect":10,"will":10,"presence":10}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]}
                }}
                """;
    }

    private String v3State() {
        return """
                {"schemaVersion":3,"rulesetVersion":1,"state":{
                  "turnNumber":1,
                  "playerCharacter":{"description":"캐릭터","stats":{"might":10,"agility":10,"intellect":10,"will":10,"presence":10}},
                  "worldState":{"premise":"세계관","flags":{}},
                  "storyMemory":{"canonicalFacts":[],"rollingSummary":"","recentTurns":[]},
                  "inventory":{"items":{},"equipment":{"slots":{}}}
                }}
                """;
    }

    private String v4State() {
        return v3State().replace("\"schemaVersion\":3", "\"schemaVersion\":4")
                .replace("\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10}",
                        "\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10},\"vitals\":{\"hp\":{\"current\":10,\"max\":10},\"mp\":{\"current\":10,\"max\":10},\"statusEffects\":{}}");
    }

    private String v5State() {
        return v4State().replace("\"schemaVersion\":4", "\"schemaVersion\":5")
                .replace("\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}",
                        "\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}},\"combatEncounter\":null");
    }

    private String v5CombatState() {
        return v4State().replace("\"schemaVersion\":4", "\"schemaVersion\":5")
                .replace("\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}",
                        "\"inventory\":{\"items\":{\"blade\":{\"id\":\"blade\",\"definition\":{\"id\":\"blade\",\"ownershipType\":\"INSTANCE\",\"equipmentSlot\":\"MAIN_HAND\"},\"quantity\":1}},\"equipment\":{\"slots\":{\"MAIN_HAND\":\"blade\"}}},\"combatEncounter\":{\"encounterId\":\"enc-1\",\"status\":\"ACTIVE\",\"enemies\":{\"wolf\":{\"enemyId\":\"wolf\",\"displayName\":\"늑대\",\"vitals\":{\"hp\":{\"current\":10,\"max\":10},\"mp\":{\"current\":10,\"max\":10},\"statusEffects\":{}}}},\"turnOrder\":[\"player\",\"wolf\"],\"currentActorId\":\"player\"}");
    }

    private String legacyStateJson() throws Exception {
        ClassPathResource resource = new ClassPathResource(LEGACY_FIXTURE);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
