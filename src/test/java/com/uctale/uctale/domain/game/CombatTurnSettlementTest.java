package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CombatTurnSettlementTest {
    @Test
    void fixedEnemyResponseReturnsControlToPlayerAndAuditsDamage() {
        GameState initial = GameState.initial("world", "hero", "start");
        PlayerAction start = new PlayerAction(1, "start-token", ActionType.GAMEPLAY, 1,
                GameplayActionRules.issueArguments(1, "START_COMBAT", "wolf"), "싸운다");
        TurnResolution started = GameplayActionRules.resolve(initial, start, new ActionResolver(),
                new QuestRules(), new RelationshipRules());
        GameState active = started.stateTransition().nextState();
        PlayerAction pass = new PlayerAction(2, "pass-token", ActionType.COMBAT_PASS, active.turnNumber(),
                Map.of("encounterId", active.combatEncounter().encounterId()), "대기한다");

        TurnResolution resolution = new ActionResolver().resolve(active, pass);
        TurnResolution settled = CombatTurnSettlement.apply(resolution);

        assertThat(settled.stateTransition().nextState().combatEncounter().currentActorId())
                .isEqualTo(CombatEncounter.PLAYER_ACTOR_ID);
        assertThat(settled.stateTransition().nextState().playerCharacter().vitals().hp().current()).isEqualTo(9);
        assertThat(VitalsRules.replay(active.playerCharacter().vitals(), settled.gameResult().stateChanges()))
                .isEqualTo(settled.stateTransition().nextState().playerCharacter().vitals());
        assertThat(CombatRules.replay(active.combatEncounter(), settled.gameResult().stateChanges(),
                settled.stateTransition().nextState().playerCharacter().vitals()))
                .isEqualTo(settled.stateTransition().nextState().combatEncounter());
    }
}
