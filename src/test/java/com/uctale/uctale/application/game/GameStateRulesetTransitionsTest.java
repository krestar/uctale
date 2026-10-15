package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameRuleset;
import com.uctale.uctale.domain.game.SkillCheck;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateRulesetTransitionsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void productionContractKeepsSkillCheckAndSnapshotOnSameRuleset() {
        assertThat(GameRuleset.CURRENT_VERSION).isEqualTo(1);
        assertThat(SkillCheck.RULESET_VERSION).isEqualTo(GameStateSnapshotFormat.CURRENT_RULESET_VERSION);
        assertThat(new GameStateRulesetTransitions().currentVersion()).isEqualTo(GameRuleset.CURRENT_VERSION);
    }

    @Test
    void currentRulesetPreservesHistoricalStateAndDoesNotMutateInput() throws Exception {
        JsonNode raw = legacyFixture();
        JsonNode original = raw.deepCopy();
        GameStateUpgrader.UpgradedSnapshot first = new GameStateUpgrader().upgrade(raw);
        GameStateUpgrader.UpgradedSnapshot second = new GameStateUpgrader().upgrade(raw);

        assertThat(first.schemaVersion()).isEqualTo(GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION);
        assertThat(first.rulesetVersion()).isEqualTo(GameRuleset.CURRENT_VERSION);
        assertThat(first.state()).isEqualTo(second.state());
        assertThat(raw).isEqualTo(original);
        assertThat(first.state().path("turnNumber")).isEqualTo(second.state().path("turnNumber"));
        assertThat(first.state().path("storyMemory")).isEqualTo(second.state().path("storyMemory"));
    }

    @Test
    void productionRejectsAllUnregisteredAndMalformedRulesetVersions() throws Exception {
        GameStateUpgrader upgrader = new GameStateUpgrader();
        for (int version : new int[]{-1, 0, 2, Integer.MAX_VALUE}) {
            JsonNode snapshot = envelope(version);
            assertThatThrownBy(() -> upgrader.upgrade(snapshot))
                    .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
        }
        ObjectNode missing = envelope(1);
        missing.remove("rulesetVersion");
        assertThatThrownBy(() -> upgrader.upgrade(missing))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
        for (String invalid : new String[]{"null", "\"1\"", "1.5", "2147483648"}) {
            String raw = "{\"schemaVersion\":10,\"rulesetVersion\":" + invalid + ",\"state\":{}}";
            assertThatThrownBy(() -> upgrader.upgrade(mapper.readTree(raw)))
                    .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("rulesetVersion");
        }
    }

    @Test
    void unregisteredRulesetFailsBeforeLegacySchemaConversion() throws Exception {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.put("rulesetVersion", 2);
        snapshot.set("state", mapper.createObjectNode());
        assertThatThrownBy(() -> new GameStateUpgrader().upgrade(snapshot))
                .isInstanceOf(GameStateSnapshotException.class)
                .hasMessageContaining("rulesetVersion");
    }

    @Test
    void explicitlyRegisteredTestOnlyChainPreservesOriginalAndIsDeterministic() throws Exception {
        JsonNode source = envelope(1);
        JsonNode original = source.deepCopy();
        GameStateUpgrader upgrader = new GameStateUpgrader(new GameStateRulesetTransitions(
                3, List.of(identityStep(1), identityStep(2))));
        var first = upgrader.upgrade(source);
        var second = upgrader.upgrade(source);

        assertThat(first.schemaVersion()).isEqualTo(10);
        assertThat(first.rulesetVersion()).isEqualTo(3);
        assertThat(first.state()).isEqualTo(source.get("state"));
        assertThat(second.state()).isEqualTo(first.state());
        assertThat(source).isEqualTo(original);
    }

    @Test
    void missingIntermediateTransitionNeverSilentlyChangesVersion() throws Exception {
        GameStateUpgrader upgrader = new GameStateUpgrader(new GameStateRulesetTransitions(
                3, List.of(identityStep(1))));
        assertThatThrownBy(() -> upgrader.upgrade(envelope(1)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("2 -> 3");
        assertThatThrownBy(() -> upgrader.upgrade(envelope(2)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("2 -> 3");
    }

    @Test
    void invalidOrDuplicateTransitionRegistrationIsRejected() {
        assertThatThrownBy(() -> new GameStateRulesetTransitions(3, List.of(identityStep(1), identityStep(1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복");
        assertThatThrownBy(() -> new GameStateRulesetTransitions(3, List.of(identityStep(0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameStateRulesetTransitions(3, List.of(identityStep(3))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidTransitionOutputAndCorruptCurrentStateAreNeverDefaulted() throws Exception {
        var nullStep = new GameStateRulesetTransitions.StatePreservingTransition() {
            public int fromVersion() { return 1; }
            public JsonNode upgrade(JsonNode state) { return null; }
        };
        GameStateUpgrader invalid = new GameStateUpgrader(new GameStateRulesetTransitions(2, List.of(nullStep)));
        assertThatThrownBy(() -> invalid.upgrade(envelope(1)))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("transition 결과");

        ObjectNode corrupt = envelope(1);
        ((ObjectNode) corrupt.get("state")).remove("inventory");
        assertThatThrownBy(() -> new GameStateUpgrader().upgrade(corrupt))
                .isInstanceOf(GameStateSnapshotException.class).hasMessageContaining("inventory/items");
    }

    private GameStateRulesetTransitions.StatePreservingTransition identityStep(int from) {
        return new GameStateRulesetTransitions.StatePreservingTransition() {
            public int fromVersion() { return from; }
            public JsonNode upgrade(JsonNode state) { return state.deepCopy(); }
        };
    }

    private ObjectNode envelope(int ruleset) throws Exception {
        JsonNode state = new GameStateUpgrader().upgrade(legacyFixture()).state();
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("schemaVersion", GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION);
        envelope.put("rulesetVersion", ruleset);
        envelope.set("state", state.deepCopy());
        return envelope;
    }

    private JsonNode legacyFixture() throws Exception {
        try (var stream = new ClassPathResource("fixtures/game-state/snapshot-v0-production.json").getInputStream()) {
            return mapper.readTree(stream);
        }
    }
}
