package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.AttackAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AttackRulesTest {
    private final AttackRules rules = new AttackRules();

    @Test @DisplayName("명중은 d20과 방어 점수, 피해는 d6와 피해 경감으로 서버가 결정한다")
    void hitAndDefense_AreServerDecided(){GameState state=activeState(10,new EnemyCombatProfile(10,2));AttackResult r=rules.resolve(state,new AttackAction("enc-1","wolf"),new SequenceRandom(10,4));assertThat(r.outcome()).isEqualTo(AttackOutcome.HIT);assertThat(r.attackTotal()).isEqualTo(10);assertThat(r.damageTotal()).isEqualTo(4);assertThat(r.damageAfterMitigation()).isEqualTo(2);assertThat(r.finalDamage()).isEqualTo(2);assertThat(r.targetHpAfter()).isEqualTo(8);}

    @Test @DisplayName("빗나감은 damage roll을 소비하지 않고 피해를 0으로 확정한다")
    void miss_DoesNotRollDamage(){SequenceRandom random=new SequenceRandom(9);AttackResult r=rules.resolve(activeState(10,new EnemyCombatProfile(10,0)),new AttackAction("enc-1","wolf"),random);assertThat(r.outcome()).isEqualTo(AttackOutcome.MISS);assertThat(r.damageRoll()).isZero();assertThat(r.finalDamage()).isZero();assertThat(random.calls).isEqualTo(1);}

    @Test @DisplayName("피해는 0 HP에서 clamp되고 defeated를 서버가 확정한다")
    void damage_ClampsAtZeroHp(){AttackResult r=rules.resolve(activeState(3,new EnemyCombatProfile(10,0)),new AttackAction("enc-1","wolf"),new SequenceRandom(20,6));assertThat(r.finalDamage()).isEqualTo(3);assertThat(r.targetHpAfter()).isZero();assertThat(r.targetDefeated()).isTrue();}

    @Test @DisplayName("장착 아이템 attack/damage modifier가 판정 원천에 포함된다")
    void equipmentModifiers_AreIncluded(){GameState state=activeState(10,new EnemyCombatProfile(12,1));OwnedItem weapon=new OwnedItem("weapon-1",new ItemDefinition("sword",ItemOwnershipType.INSTANCE,EquipmentSlot.MAIN_HAND,new ItemCombatModifiers(2,3)),1);Inventory inventory=new Inventory(Map.of(weapon.id(),weapon),new Equipment(Map.of(EquipmentSlot.MAIN_HAND,weapon.id())));state=state.withRuleState(inventory,state.playerCharacter().vitals(),state.combatEncounter());AttackResult r=rules.resolve(state,new AttackAction("enc-1","wolf"),new SequenceRandom(10,2));assertThat(r.equipmentAttackModifier()).isEqualTo(2);assertThat(r.attackTotal()).isEqualTo(12);assertThat(r.equipmentDamageModifier()).isEqualTo(3);assertThat(r.finalDamage()).isEqualTo(4);}

    private GameState activeState(int hp,EnemyCombatProfile profile){GameState initial=GameState.initial("세계","캐릭터","오프닝");EnemyState enemy=new EnemyState("wolf","늑대",new CharacterVitals(new ResourcePool(hp,10),ResourcePool.full(10),Map.of()),profile);CombatEncounter pending=CombatEncounter.pending("enc-1",List.of(enemy));return initial.withCombatEncounter(CombatRules.activate(pending,initial.playerCharacter().vitals()).encounter());}
    private static final class SequenceRandom implements RandomSource{private final Deque<Integer> values=new ArrayDeque<>();private int calls;private SequenceRandom(int... values){for(int v:values)this.values.add(v);}public int nextIntInclusive(int min,int max){calls++;int v=values.removeFirst();assertThat(v).isBetween(min,max);return v;}}
}
