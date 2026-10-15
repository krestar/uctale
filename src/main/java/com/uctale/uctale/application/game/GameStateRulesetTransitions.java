package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameRuleset;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit, read-only compatibility boundary for canonical ruleset changes.
 * Ruleset 1 -> 2 only changes consequences of future Skill Checks, not the
 * interpretation of stored GameState. No historical result is re-evaluated.
 */
final class GameStateRulesetTransitions {
    private final int currentVersion;
    private final Map<Integer, StatePreservingTransition> transitions;

    GameStateRulesetTransitions() {
        this(GameRuleset.CURRENT_VERSION, List.of(new StatePreservingTransition() {
            public int fromVersion() { return 1; }
            public JsonNode upgrade(JsonNode state) { return state.deepCopy(); }
        }));
    }

    // Test-only configurable target permits exercising migration mechanics without
    // pretending that an unreviewed production ruleset 1 -> 2 transition is safe.
    GameStateRulesetTransitions(int currentVersion, List<StatePreservingTransition> transitions) {
        if (currentVersion < 1) throw new IllegalArgumentException("ruleset target version은 1 이상이어야 합니다.");
        Objects.requireNonNull(transitions, "transitions");
        Map<Integer, StatePreservingTransition> registered = new HashMap<>();
        for (StatePreservingTransition transition : transitions) {
            Objects.requireNonNull(transition, "transition");
            int from = transition.fromVersion();
            if (from < 1 || from >= currentVersion) {
                throw new IllegalArgumentException("유효하지 않은 ruleset transition 시작 버전입니다: " + from);
            }
            if (registered.putIfAbsent(from, transition) != null) {
                throw new IllegalArgumentException("중복 ruleset transition입니다: " + from);
            }
        }
        this.currentVersion = currentVersion;
        this.transitions = Map.copyOf(registered);
    }

    int currentVersion() {
        return currentVersion;
    }

    void requireSupported(int sourceVersion) {
        if (sourceVersion < 1) {
            throw new GameStateSnapshotException("유효하지 않은 snapshot rulesetVersion입니다: " + sourceVersion);
        }
        if (sourceVersion > currentVersion) {
            throw new GameStateSnapshotException("지원하지 않는 미래 snapshot rulesetVersion입니다: " + sourceVersion);
        }
        for (int version = sourceVersion; version < currentVersion; version++) {
            if (!transitions.containsKey(version)) {
                throw new GameStateSnapshotException("호환되지 않는 snapshot rulesetVersion입니다: "
                        + sourceVersion + " (전환 " + version + " -> " + (version + 1) + " 없음)");
            }
        }
    }

    JsonNode upgrade(int sourceVersion, JsonNode state) {
        requireSupported(sourceVersion);
        JsonNode result = Objects.requireNonNull(state, "state").deepCopy();
        for (int version = sourceVersion; version < currentVersion; version++) {
            // 전환 구현은 입력을 수정해도 원본 snapshot에 영향을 주지 않도록 격리한다.
            result = transitions.get(version).upgrade(result.deepCopy());
            if (result == null || !result.isObject()) {
                throw new GameStateSnapshotException("ruleset transition 결과가 손상되었습니다: " + version);
            }
        }
        return result;
    }

    /**
     * Exactly one ruleset step (fromVersion -> fromVersion + 1).
     * The implementation must be deterministic, state-preserving, and must not
     * re-roll/re-evaluate historical results or call providers.
     */
    interface StatePreservingTransition {
        int fromVersion();
        JsonNode upgrade(JsonNode state);
    }
}
