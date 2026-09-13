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
    @DisplayName("production legacy raw GameState를 schema v4, 기본 stats/inventory/vitals로 순수 upgrade한다")
    void legacyRawState_IsUpgradedToCurrentSchema() throws Exception {
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(objectMapper.readTree(legacyStateJson()));
        assertThat(upgraded.schemaVersion()).isEqualTo(4);
        assertThat(upgraded.rulesetVersion()).isEqualTo(1);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(10);
        assertThat(upgraded.state().get("inventory").get("items").isEmpty()).isTrue();
        assertDefaultVitals(upgraded.state());
    }

    @Test
    @DisplayName("schema v1 legacy stats의 canonical 값은 v4까지 보존한다")
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
        assertThat(upgraded.schemaVersion()).isEqualTo(4);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("might").asInt()).isEqualTo(14);
        assertThat(upgraded.state().get("playerCharacter").get("stats").get("agility").asInt()).isEqualTo(12);
        assertDefaultVitals(upgraded.state());
    }

    @Test
    @DisplayName("schema v2는 빈 inventory와 기본 vitals를 순차적으로 추가한다")
    void schemaV2_IsUpgradedThroughV4() throws Exception {
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(objectMapper.readTree(v2State()));
        assertThat(upgraded.schemaVersion()).isEqualTo(4);
        assertThat(upgraded.state().get("inventory").get("items").isEmpty()).isTrue();
        assertDefaultVitals(upgraded.state());
    }

    @Test
    @DisplayName("schema v3는 기존 state 의미를 보존하고 기본 vitals만 명시적으로 추가한다")
    void schemaV3_AddsDefaultVitals() throws Exception {
        JsonNode snapshot = objectMapper.readTree(v3State());
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        assertThat(upgraded.schemaVersion()).isEqualTo(4);
        assertThat(upgraded.state().get("inventory")).isEqualTo(snapshot.get("state").get("inventory"));
        assertDefaultVitals(upgraded.state());
    }

    @Test
    @DisplayName("schema v4 snapshot은 state를 그대로 읽는다")
    void schemaV4_IsReadWithoutMutation() throws Exception {
        JsonNode snapshot = objectMapper.readTree(v4State());
        GameStateUpgrader.UpgradedSnapshot upgraded = upgrader.upgrade(snapshot);
        assertThat(upgraded.schemaVersion()).isEqualTo(4);
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
        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("MIGHT");
    }

    @Test
    @DisplayName("schema v2에 inventory가 있으면 정의되지 않은 의미를 추정하지 않는다")
    void schemaV2WithInventory_FailsExplicitly() throws Exception {
        String json = v2State().replace("\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]}",
                "\"storyMemory\":{\"canonicalFacts\":[],\"rollingSummary\":\"\",\"recentTurns\":[]},\"inventory\":{\"items\":{},\"equipment\":{\"slots\":{}}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v2").hasMessageContaining("inventory");
    }

    @Test
    @DisplayName("schema v3에 vitals가 이미 있으면 정의되지 않은 의미를 canonical v4로 승격하지 않는다")
    void schemaV3WithVitals_FailsExplicitly() throws Exception {
        String json = v3State().replace("\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10}",
                "\"stats\":{\"might\":10,\"agility\":10,\"intellect\":10,\"will\":10,\"presence\":10},\"vitals\":{\"hp\":{\"current\":1,\"max\":1},\"mp\":{\"current\":1,\"max\":1},\"statusEffects\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(objectMapper.readTree(json)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schema v3").hasMessageContaining("vitals");
    }

    @Test
    @DisplayName("미래 schema version은 기본값 처리하지 않고 실패한다")
    void futureSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":5,\"rulesetVersion\":1,\"state\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("미래 snapshot schemaVersion");
    }

    @Test
    @DisplayName("미지원 ruleset version은 자동 재판정하지 않고 실패한다")
    void unsupportedRulesetVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":4,\"rulesetVersion\":2,\"state\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
    }

    @Test
    @DisplayName("envelope에서 schemaVersion만 누락된 손상 snapshot은 legacy로 오인하지 않는다")
    void damagedEnvelopeMissingSchemaVersion_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"rulesetVersion\":1,\"state\":{}}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("schemaVersion이 누락");
    }

    @Test
    @DisplayName("schemaVersion이 있어도 state가 누락되면 손상 snapshot으로 실패한다")
    void missingState_FailsExplicitly() throws Exception {
        JsonNode snapshot = objectMapper.readTree("{\"schemaVersion\":4,\"rulesetVersion\":1}");
        assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("state가 누락");
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

    private String legacyStateJson() throws Exception {
        ClassPathResource resource = new ClassPathResource(LEGACY_FIXTURE);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
