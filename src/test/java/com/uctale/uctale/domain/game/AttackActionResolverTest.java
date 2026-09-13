package com.uctale.uctale.domain.game;

import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttackActionResolverTest {
    private final ActionResolver resolver=new ActionResolver();

    @Test @DisplayName("공격 판정과 enemy HP 감소는 하나의 TurnResolution에서 원자적으로 확정된다")
    void attackAndHpChange_AreAtomic(){GameState state=activeState();PlayerAction action=attack(state,"wolf");AttackResult rolled=resolver.rollAttack(state,action,(min,max)->max);TurnResolution resolution=resolver.resolveAttack(state,action,rolled);EnemyState enemy=resolution.stateTransition().nextState().combatEncounter().requireEnemy("wolf");assertThat(enemy.vitals().hp().current()).isEqualTo(4);assertThat(resolution.gameResult().attackResult()).isEqualTo(rolled);assertThat(resolution.gameResult().stateChanges()).anyMatch(GameResult.AttackResolved.class::isInstance).anyMatch(GameResult.CombatEncounterChanged.class::isInstance);NarrativeContext context=NarrativeContext.from("result-1",resolution);assertThat(context.stateChanges()).anyMatch(GameResult.AttackResolved.class::isInstance);}

    @Test @DisplayName("존재하지 않는 target은 random/provider 경계 전에 거절된다")
    void invalidTarget_IsRejectedBeforeRoll(){GameState state=activeState();AtomicInteger calls=new AtomicInteger();PlayerAction action=attack(state,"ghost");assertThatThrownBy(()->resolver.rollAttack(state,action,(min,max)->{calls.incrementAndGet();return min;})).isInstanceOf(IllegalArgumentException.class);assertThat(calls).hasValue(0);}

    @Test @DisplayName("player turn이 아니면 공격은 random/provider 경계 전에 거절된다")
    void wrongActor_IsRejectedBeforeRoll(){GameState player=activeState();CombatEncounter enemyTurn=CombatRules.advanceActor(player.combatEncounter(),player.playerCharacter().vitals()).encounter();GameState state=player.withCombatEncounter(enemyTurn);AtomicInteger calls=new AtomicInteger();assertThatThrownBy(()->resolver.rollAttack(state,attack(state,"wolf"),(min,max)->{calls.incrementAndGet();return min;})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("player");assertThat(calls).hasValue(0);}

    private GameState activeState(){GameState initial=GameState.initial("세계","캐릭터","오프닝");EnemyState enemy=new EnemyState("wolf","늑대",new CharacterVitals(new ResourcePool(10,10),ResourcePool.full(10),Map.of()),new EnemyCombatProfile(10,0));CombatEncounter pending=CombatEncounter.pending("enc-1",List.of(enemy));return initial.withCombatEncounter(CombatRules.activate(pending,initial.playerCharacter().vitals()).encounter());}
    private PlayerAction attack(GameState state,String target){return new PlayerAction(1,"token",ActionType.COMBAT_ATTACK,state.turnNumber(),Map.of("encounterId","enc-1","targetEnemyId",target),"공격한다");}
}
