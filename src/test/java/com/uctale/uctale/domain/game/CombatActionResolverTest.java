package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombatActionResolverTest {

    private final ActionResolver resolver = new ActionResolver();

    @Test
    @DisplayName("combat action은 active encounter와 player turn 없이는 provider 호출 전 resolution에서 거절된다")
    void combatAction_RequiresActivePlayerTurn() {
        GameState noCombat = GameState.initial("세계", "캐릭터", "오프닝");
        assertThatThrownBy(() -> resolver.resolve(noCombat, combatAction(noCombat, ActionType.COMBAT_PASS, "enc-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("활성 combat encounter");

        CombatEncounter pending = CombatEncounter.pending("enc-1", List.of(enemy("e1", 10)));
        GameState pendingState = noCombat.withCombatEncounter(pending);
        assertThatThrownBy(() -> resolver.resolve(pendingState, combatAction(pendingState, ActionType.COMBAT_PASS, "enc-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("활성 combat encounter");
    }

    @Test
    @DisplayName("active combat에서는 일반 narrative action으로 combat 규칙을 우회할 수 없다")
    void activeCombat_RejectsNarrativeBypass() {
        GameState state = activeState();
        PlayerAction narrative = new PlayerAction(1, "token", ActionType.NARRATIVE_CHOICE, state.turnNumber(), Map.of("choiceId", "1"), "무시한다");
        assertThatThrownBy(() -> resolver.resolve(state, narrative))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("combat action만");
    }

    @Test
    @DisplayName("server-issued pass는 다음 행동 가능한 actor로 전진하고 typed combat change를 남긴다")
    void pass_AdvancesActorAndAuditsTransition() {
        GameState state = activeState();
        TurnResolution resolution = resolver.resolve(state, combatAction(state, ActionType.COMBAT_PASS, "enc-1"));
        assertThat(resolution.stateTransition().nextState().combatEncounter().currentActorId()).isEqualTo("e1");
        assertThat(resolution.gameResult().stateChanges()).anyMatch(GameResult.CombatEncounterChanged.class::isInstance);
    }

    @Test
    @DisplayName("player escape는 encounter를 ESCAPED로 종료한다")
    void escape_EndsEncounter() {
        GameState state = activeState();
        TurnResolution resolution = resolver.resolve(state, combatAction(state, ActionType.COMBAT_ESCAPE, "enc-1"));
        assertThat(resolution.stateTransition().nextState().combatEncounter().status()).isEqualTo(CombatEncounterStatus.ESCAPED);
        assertThat(resolution.stateTransition().nextState().combatEncounter().currentActorId()).isNull();
    }

    private GameState activeState() {
        GameState initial = GameState.initial("세계", "캐릭터", "오프닝");
        CombatEncounter pending = CombatEncounter.pending("enc-1", List.of(enemy("e1", 10)));
        return initial.withCombatEncounter(CombatRules.activate(pending, initial.playerCharacter().vitals()).encounter());
    }

    private PlayerAction combatAction(GameState state, ActionType type, String encounterId) {
        return new PlayerAction(1, "token", type, state.turnNumber(), Map.of("encounterId", encounterId), type.name());
    }

    private EnemyState enemy(String id, int hp) {
        return new EnemyState(id, id, new CharacterVitals(new ResourcePool(hp, 10), ResourcePool.full(10), Map.of()));
    }
}
