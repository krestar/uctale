package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Completes a single deterministic NPC response within the player's HTTP progress turn.
 * No provider-supplied damage, dice, or extra canonical commits are used.
 */
public final class CombatTurnSettlement {
    private CombatTurnSettlement() {}

    public static TurnResolution apply(TurnResolution resolution) {
        Objects.requireNonNull(resolution, "TurnResolution은 필수입니다.");
        GameState nextState = resolution.stateTransition().nextState();
        CombatEncounter encounter = nextState.combatEncounter();
        if (encounter == null || !encounter.active()
                || CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())) {
            return resolution;
        }
        EnemyState enemy = encounter.requireEnemy(encounter.currentActorId());
        if (!enemy.canAct()) throw new IllegalStateException("행동 불가능한 적이 현재 actor입니다.");

        // For now, one enemy has one bounded, deterministic response per player action.
        VitalsRules.Result damage = new VitalsRules().apply(nextState.playerCharacter().vitals(),
                List.of(new VitalsCommand.Damage(1)));
        CombatRules.Result advanced = CombatRules.advanceActor(encounter, damage.vitals());
        GameState next = nextState.withRuleState(nextState.inventory(), damage.vitals(),
                advanced.encounter(), nextState.abilityState());

        List<GameResult.StateChange> changes = new ArrayList<>(resolution.gameResult().stateChanges());
        int turnIndex = -1;
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i) instanceof GameResult.TurnAdvanced) {
                turnIndex = i;
                break;
            }
        }
        if (turnIndex < 0) throw new IllegalStateException("TURN_ADVANCED audit이 없습니다.");
        changes.addAll(turnIndex, damage.stateChanges());
        changes.addAll(turnIndex + damage.stateChanges().size(), advanced.stateChanges());

        List<GameResult.GameEvent> events = new ArrayList<>(resolution.gameResult().events());
        if (!events.contains(GameResult.GameEvent.COMBAT_ENCOUNTER_CHANGED)) {
            events.add(GameResult.GameEvent.COMBAT_ENCOUNTER_CHANGED);
        }
        GameResult previous = resolution.gameResult();
        List<String> cues = new ArrayList<>(previous.narrativeCues());
        cues.add("적의 반격은 서버 확정 결과이며 HP 감소량은 1이다.");
        GameResult result = new GameResult(previous.resolvedAction(), previous.outcome(),
                previous.skillCheckResult(), previous.attackResult(), previous.canonicalFacts(),
                events, changes, cues);
        return new TurnResolution(result, new StateTransition(resolution.stateTransition().previousState(), next));
    }
}
