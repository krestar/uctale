package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VitalsRulesTest {

    private final VitalsRules rules = new VitalsRules();

    @Test
    @DisplayName("damage와 heal은 HP를 0..max 범위로 clamp하고 실제 delta를 기록한다")
    void damageAndHeal_ClampAndAuditActualDelta() {
        CharacterVitals initial = CharacterVitals.defaults();

        VitalsRules.Result damaged = rules.apply(initial, List.of(new VitalsCommand.Damage(Integer.MAX_VALUE)));
        assertThat(damaged.vitals().hp()).isEqualTo(new ResourcePool(0, CharacterVitals.DEFAULT_MAX_HP));
        assertThat(damaged.vitals().defeated()).isTrue();
        assertThat(damaged.vitals().incapacitated()).isTrue();
        assertThat(damaged.stateChanges()).containsExactly(new GameResult.VitalsChanged(
                GameResult.VitalResource.HP, 10, 0, -10, GameResult.VitalsChangeReason.DAMAGE));

        VitalsRules.Result healed = rules.apply(damaged.vitals(), List.of(new VitalsCommand.Heal(Integer.MAX_VALUE)));
        assertThat(healed.vitals().hp()).isEqualTo(ResourcePool.full(CharacterVitals.DEFAULT_MAX_HP));
        assertThat(healed.stateChanges()).containsExactly(new GameResult.VitalsChanged(
                GameResult.VitalResource.HP, 0, 10, 10, GameResult.VitalsChangeReason.HEAL));
    }

    @Test
    @DisplayName("MP 소비는 부족하면 거절하고 restore는 max를 넘지 않는다")
    void manaTransitions_ValidateSpendAndClampRestore() {
        CharacterVitals initial = CharacterVitals.defaults();
        VitalsRules.Result spent = rules.apply(initial, List.of(new VitalsCommand.SpendMana(7)));
        assertThat(spent.vitals().mp().current()).isEqualTo(3);

        assertThatThrownBy(() -> rules.apply(spent.vitals(), List.of(new VitalsCommand.SpendMana(4))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("현재 MP");

        VitalsRules.Result restored = rules.apply(spent.vitals(), List.of(new VitalsCommand.RestoreMana(Integer.MAX_VALUE)));
        assertThat(restored.vitals().mp().current()).isEqualTo(10);
        assertThat(restored.stateChanges()).containsExactly(new GameResult.VitalsChanged(
                GameResult.VitalResource.MP, 3, 10, 7, GameResult.VitalsChangeReason.RESTORE));
    }

    @Test
    @DisplayName("status는 같은 definitionId를 암묵적으로 merge하지 않고 명시적 update만 허용한다")
    void statusEffect_RequiresExplicitUpdate() {
        StatusEffect poison = effect("poison", 1, 2, 3, false);
        CharacterVitals applied = rules.apply(CharacterVitals.defaults(), List.of(new VitalsCommand.ApplyStatus(poison))).vitals();

        assertThatThrownBy(() -> rules.apply(applied, List.of(new VitalsCommand.ApplyStatus(poison))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복 적용");

        StatusEffect stacked = effect("poison", 2, 3, 4, false);
        VitalsRules.Result updated = rules.apply(applied, List.of(new VitalsCommand.UpdateStatus(stacked)));
        assertThat(updated.vitals().requireStatus("poison")).isEqualTo(stacked);
        assertThat(updated.stateChanges()).containsExactly(new GameResult.StatusEffectUpdated(poison, stacked));
    }

    @Test
    @DisplayName("새로 적용한 status는 같은 turn의 END_OF_TURN에서 감소하지 않고 다음 turn부터 정확히 한 번씩 감소·만료한다")
    void durationTiming_DoesNotTickFreshEffectAndExpiresDeterministically() {
        StatusEffect stun = effect("stun", 1, 1, 2, true);
        VitalsRules.Result applied = rules.apply(CharacterVitals.defaults(), List.of(
                new VitalsCommand.ApplyStatus(stun),
                new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN)
        ));
        assertThat(applied.vitals().requireStatus("stun").remainingTurns()).isEqualTo(2);
        assertThat(applied.vitals().incapacitated()).isTrue();

        VitalsRules.Result ticked = rules.apply(applied.vitals(), List.of(
                new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN)
        ));
        assertThat(ticked.vitals().requireStatus("stun").remainingTurns()).isEqualTo(1);
        assertThat(ticked.stateChanges()).containsExactly(new GameResult.StatusDurationChanged("stun", 2, 1));

        VitalsRules.Result expired = rules.apply(ticked.vitals(), List.of(
                new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN)
        ));
        assertThat(expired.vitals().statusEffects()).doesNotContainKey("stun");
        assertThat(expired.stateChanges()).containsExactly(
                new GameResult.StatusEffectRemoved(ticked.vitals().requireStatus("stun"), GameResult.StatusRemovalReason.EXPIRED));
    }

    @Test
    @DisplayName("같은 expiry timing을 한 transition에서 두 번 진행할 수 없다")
    void durationTiming_RejectsDuplicateAdvance() {
        assertThatThrownBy(() -> rules.apply(CharacterVitals.defaults(), List.of(
                new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN),
                new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN)
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("두 번");
    }

    @Test
    @DisplayName("audit replay는 이전 canonical 값이 다르면 손상된 ledger로 거절한다")
    void replay_RejectsTamperedPreviousValue() {
        GameResult.VitalsChanged tampered = new GameResult.VitalsChanged(
                GameResult.VitalResource.HP, 9, 5, -4, GameResult.VitalsChangeReason.DAMAGE);
        assertThatThrownBy(() -> VitalsRules.replay(CharacterVitals.defaults(), List.of(tampered)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("이전 값");
    }

    @Test
    @DisplayName("resource pool과 status runtime 값은 경계 밖 값을 허용하지 않는다")
    void valueObjects_RejectInvalidBoundaries() {
        assertThatThrownBy(() -> new ResourcePool(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourcePool(11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourcePool(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> effect("poison", 0, 1, 1, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> effect("poison", 1, 1, 0, false)).isInstanceOf(IllegalArgumentException.class);
    }

    private StatusEffect effect(String id, int stacks, int intensity, int turns, boolean incapacitating) {
        return new StatusEffect(id, stacks, intensity, turns, StatusExpiryTrigger.END_OF_TURN, incapacitating);
    }
}
