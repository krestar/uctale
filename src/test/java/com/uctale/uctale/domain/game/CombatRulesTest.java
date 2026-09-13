package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombatRulesTest {

    @Test
    @DisplayName("pending encounter는 enemy ID 정렬 기반 결정적 turn order로 활성화된다")
    void activate_UsesDeterministicTurnOrder() {
        CombatEncounter pending = CombatEncounter.pending("enc-1", List.of(enemy("zombie", 10), enemy("bandit", 10)));

        CombatEncounter active = CombatRules.activate(pending, CharacterVitals.defaults()).encounter();

        assertThat(active.status()).isEqualTo(CombatEncounterStatus.ACTIVE);
        assertThat(active.turnOrder()).containsExactly("player", "bandit", "zombie");
        assertThat(active.currentActorId()).isEqualTo("player");
    }

    @Test
    @DisplayName("player 패배 또는 모든 enemy 패배만 encounter 종료 조건이다")
    void terminalConditions_AreDeterministic() {
        Map<String, EnemyState> alive = Map.of("enemy", enemy("enemy", 1));
        assertThat(CombatRules.shouldResolve(CharacterVitals.defaults(), alive)).isFalse();
        assertThat(CombatRules.shouldResolve(vitals(0, 10), alive)).isTrue();
        assertThat(CombatRules.shouldResolve(CharacterVitals.defaults(), Map.of("enemy", enemy("enemy", 0)))).isTrue();
    }

    @Test
    @DisplayName("defeated enemy는 서버 부활 규칙 없이 되살리거나 current actor로 둘 수 없다")
    void defeatedEnemy_CannotReviveOrAct() {
        EnemyState defeated = enemy("enemy", 0);
        assertThatThrownBy(() -> defeated.withVitals(vitals(1, 10)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("되살릴 수 없습니다");
        assertThatThrownBy(() -> new CombatEncounter("enc-1", CombatEncounterStatus.ACTIVE,
                Map.of("enemy", defeated), List.of("player", "enemy"), "enemy"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("행동 불가능");
    }

    @Test
    @DisplayName("participant 참가와 이탈은 pending에서만 허용되고 마지막 enemy는 제거할 수 없다")
    void participantMembership_RequiresPendingEncounter() {
        CombatEncounter pending = CombatEncounter.pending("enc-1", List.of(enemy("a", 10)));
        CombatEncounter joined = CombatRules.joinEnemy(pending, enemy("b", 10)).encounter();
        assertThat(joined.enemies()).containsOnlyKeys("a", "b");
        CombatEncounter left = CombatRules.leaveEnemy(joined, "b").encounter();
        assertThat(left.enemies()).containsOnlyKeys("a");
        assertThatThrownBy(() -> CombatRules.leaveEnemy(left, "a")).isInstanceOf(IllegalStateException.class);
        CombatEncounter active = CombatRules.activate(left, CharacterVitals.defaults()).encounter();
        assertThatThrownBy(() -> CombatRules.joinEnemy(active, enemy("b", 10))).isInstanceOf(IllegalStateException.class);
    }

    private EnemyState enemy(String id, int hp) {
        return new EnemyState(id, id, vitals(hp, 10));
    }

    private CharacterVitals vitals(int hp, int maxHp) {
        return new CharacterVitals(new ResourcePool(hp, maxHp), ResourcePool.full(10), Map.of());
    }
}
