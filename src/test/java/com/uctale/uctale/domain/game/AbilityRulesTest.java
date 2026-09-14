package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbilityRulesTest {
    private final ActionResolver resolver = new ActionResolver();

    @Test
    @DisplayName("damage ability는 MP 소비, enemy HP 감소, cooldown을 한 transition에서 확정한다")
    void damageAbility_IsAtomic() {
        GameState state = activeCombat(GameState.initial("세계", "캐릭터", "오프닝"));

        TurnResolution resolution = resolver.resolve(state, ability(state, "arcane-bolt", "wolf"));
        GameState next = resolution.stateTransition().nextState();

        assertThat(next.playerCharacter().vitals().mp().current()).isEqualTo(7);
        assertThat(next.combatEncounter().requireEnemy("wolf").vitals().hp().current()).isEqualTo(6);
        assertThat(next.abilityState().remainingCooldown("arcane-bolt")).isEqualTo(2);
        assertThat(resolution.gameResult().events()).contains(GameResult.GameEvent.ABILITY_RESOLVED);
        assertThat(resolution.gameResult().stateChanges()).anyMatch(GameResult.AbilityResolved.class::isInstance);
    }

    @Test
    @DisplayName("heal ability는 server-owned HP/MP/cooldown 결과를 만든다")
    void healAbility_UsesFixtureRules() {
        GameState base = GameState.initial("세계", "캐릭터", "오프닝");
        CharacterVitals wounded = base.playerCharacter().vitals().withHp(new ResourcePool(5, 10));
        GameState state = activeCombat(base.withPlayerVitals(wounded));

        GameState next = resolver.resolve(state, ability(state, "second-wind", CombatEncounter.PLAYER_ACTOR_ID))
                .stateTransition().nextState();

        assertThat(next.playerCharacter().vitals().hp().current()).isEqualTo(9);
        assertThat(next.playerCharacter().vitals().mp().current()).isEqualTo(8);
        assertThat(next.abilityState().remainingCooldown("second-wind")).isEqualTo(2);
    }

    @Test
    @DisplayName("status ability는 fixture status를 적용하고 같은 턴의 duration tick으로 즉시 소모하지 않는다")
    void statusAbility_AppliesStatusWithoutImmediateExpiry() {
        GameState state = activeCombat(GameState.initial("세계", "캐릭터", "오프닝"));

        GameState next = resolver.resolve(state, ability(state, "steady-focus", CombatEncounter.PLAYER_ACTOR_ID))
                .stateTransition().nextState();

        assertThat(next.playerCharacter().vitals().requireStatus("focused").remainingTurns()).isEqualTo(2);
        assertThat(next.playerCharacter().vitals().mp().current()).isEqualTo(8);
        assertThat(next.abilityState().remainingCooldown("steady-focus")).isEqualTo(3);
    }

    @Test
    @DisplayName("MP 부족, cooldown 중, 잘못된 target은 canonical state 변경 전에 거절한다")
    void rejectsUnavailableAbilities() {
        GameState active = activeCombat(GameState.initial("세계", "캐릭터", "오프닝"));
        GameState lowMp = active.withPlayerVitals(active.playerCharacter().vitals().withMp(new ResourcePool(2, 10)));
        assertThatThrownBy(() -> resolver.resolve(lowMp, ability(lowMp, "arcane-bolt", "wolf")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MP");

        GameState cooling = active.withAbilityState(new AbilityState(Map.of("arcane-bolt", 1)));
        assertThatThrownBy(() -> resolver.resolve(cooling, ability(cooling, "arcane-bolt", "wolf")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cooldown");

        assertThatThrownBy(() -> resolver.resolve(active, ability(active, "arcane-bolt", CombatEncounter.PLAYER_ACTOR_ID)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("enemy");
        assertThat(active.playerCharacter().vitals().mp().current()).isEqualTo(10);
        assertThat(active.abilityState().cooldowns()).isEmpty();
    }

    @Test
    @DisplayName("cooldown은 완료된 다음 턴들에서만 감소하고 사용 턴에는 새 cooldown이 감소하지 않는다")
    void cooldownTiming_IsTurnBased() {
        GameState active = activeCombat(GameState.initial("세계", "캐릭터", "오프닝"));
        GameState afterAbility = resolver.resolve(active, ability(active, "arcane-bolt", "wolf"))
                .stateTransition().nextState();
        assertThat(afterAbility.abilityState().remainingCooldown("arcane-bolt")).isEqualTo(2);

        GameState playerTurnAgain = withPlayerTurn(afterAbility);
        PlayerAction pass = new PlayerAction(90, "token", ActionType.COMBAT_PASS, playerTurnAgain.turnNumber(),
                Map.of("encounterId", playerTurnAgain.combatEncounter().encounterId()), "기다린다");
        GameState afterOtherTurn = resolver.resolve(playerTurnAgain, pass).stateTransition().nextState();

        assertThat(afterOtherTurn.abilityState().remainingCooldown("arcane-bolt")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이전 state와 ability action 재평가는 동일 결과를 만들고 stale 재적용은 거절된다")
    void retryBoundary_IsDeterministicAndStaleSafe() {
        GameState state = activeCombat(GameState.initial("세계", "캐릭터", "오프닝"));
        PlayerAction action = ability(state, "arcane-bolt", "wolf");

        TurnResolution first = resolver.resolve(state, action);
        TurnResolution retried = resolver.resolve(state, action);
        assertThat(retried).isEqualTo(first);

        assertThatThrownBy(() -> resolver.resolve(first.stateTransition().nextState(), action))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source turn");
    }

    private GameState activeCombat(GameState state) {
        EnemyState enemy = new EnemyState("wolf", "늑대", CharacterVitals.defaults());
        CombatEncounter pending = CombatRules.start(null, "enc-1", java.util.List.of(enemy)).encounter();
        CombatEncounter active = CombatRules.activate(pending, state.playerCharacter().vitals()).encounter();
        return state.withCombatEncounter(active);
    }

    private GameState withPlayerTurn(GameState state) {
        CombatEncounter encounter = state.combatEncounter();
        if (CombatEncounter.PLAYER_ACTOR_ID.equals(encounter.currentActorId())) return state;
        CombatEncounter advanced = CombatRules.advanceActor(encounter, state.playerCharacter().vitals()).encounter();
        return state.withCombatEncounter(advanced);
    }

    private PlayerAction ability(GameState state, String definitionId, String targetId) {
        return new PlayerAction(50, "token", ActionType.COMBAT_ABILITY, state.turnNumber(), Map.of(
                "encounterId", state.combatEncounter().encounterId(),
                "abilityDefinitionId", definitionId,
                "targetId", targetId
        ), definitionId + " 사용");
    }
}
