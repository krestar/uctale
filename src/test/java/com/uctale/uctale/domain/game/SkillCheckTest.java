package com.uctale.uctale.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillCheckTest {

    @Test
    @DisplayName("신규 캐릭터는 모든 능력치 10의 서버 기본값을 사용한다")
    void defaults_AreAppliedToNewCharacter() {
        PlayerCharacter character = PlayerCharacter.initial("캐릭터");

        assertThat(character.stats()).isEqualTo(CharacterStats.defaults());
        assertThat(character.stats().score(StatType.MIGHT)).isEqualTo(10);
        assertThat(character.stats().score(StatType.PRESENCE)).isEqualTo(10);
    }

    @Test
    @DisplayName("능력치 modifier는 10 기준 2점당 1이며 음수는 내림 계산한다")
    void statModifier_UsesFloorDivision() {
        CharacterStats stats = new CharacterStats(9, 10, 11, 12, 30);

        assertThat(stats.modifier(StatType.MIGHT)).isEqualTo(-1);
        assertThat(stats.modifier(StatType.AGILITY)).isZero();
        assertThat(stats.modifier(StatType.INTELLECT)).isZero();
        assertThat(stats.modifier(StatType.WILL)).isEqualTo(1);
        assertThat(stats.modifier(StatType.PRESENCE)).isEqualTo(10);
    }

    @Test
    @DisplayName("고정 roll과 동일 입력은 같은 감사 가능한 SkillCheckResult를 만든다")
    void fixedRoll_IsDeterministicAndAuditable() {
        CharacterStats stats = new CharacterStats(14, 10, 10, 10, 10);
        SkillCheck check = new SkillCheck(StatType.MIGHT, new Difficulty(15), 1);

        SkillCheckResult first = check.resolve(stats, new FixedRandomSource(12));
        SkillCheckResult second = check.resolve(stats, new FixedRandomSource(12));

        assertThat(first).isEqualTo(second);
        assertThat(first.rawRoll()).isEqualTo(12);
        assertThat(first.statModifier()).isEqualTo(2);
        assertThat(first.situationalModifier()).isEqualTo(1);
        assertThat(first.dc()).isEqualTo(15);
        assertThat(first.total()).isEqualTo(15);
        assertThat(first.outcome()).isEqualTo(SkillCheckOutcome.SUCCESS);
        assertThat(first.rulesetVersion()).isEqualTo(SkillCheck.RULESET_VERSION);
    }

    @Test
    @DisplayName("natural 1과 20에 특수 성공 실패 규칙을 적용하지 않는다")
    void naturalOneAndTwenty_HaveNoSpecialRule() {
        CharacterStats neutral = CharacterStats.defaults();

        SkillCheckResult naturalOneSuccess = new SkillCheck(
                StatType.MIGHT, new Difficulty(20), 19
        ).resolve(neutral, new FixedRandomSource(1));
        SkillCheckResult naturalTwentyFailure = new SkillCheck(
                StatType.MIGHT, new Difficulty(21), 0
        ).resolve(neutral, new FixedRandomSource(20));

        assertThat(naturalOneSuccess.total()).isEqualTo(20);
        assertThat(naturalOneSuccess.outcome()).isEqualTo(SkillCheckOutcome.SUCCESS);
        assertThat(naturalTwentyFailure.total()).isEqualTo(20);
        assertThat(naturalTwentyFailure.outcome()).isEqualTo(SkillCheckOutcome.FAILURE);
    }

    @Test
    @DisplayName("sequence RandomSource는 테스트에서 roll 순서를 결정적으로 제어한다")
    void sequenceRandomSource_ControlsRollOrder() {
        SequenceRandomSource random = new SequenceRandomSource(3, 19);
        SkillCheck check = new SkillCheck(StatType.AGILITY, new Difficulty(10), 0);

        assertThat(check.resolve(CharacterStats.defaults(), random).rawRoll()).isEqualTo(3);
        assertThat(check.resolve(CharacterStats.defaults(), random).rawRoll()).isEqualTo(19);
    }

    @Test
    @DisplayName("능력치 DC roll 상황 modifier invariant를 생성 시점에 검증한다")
    void invalidRuleValues_AreRejected() {
        assertThatThrownBy(() -> new CharacterStats(0, 10, 10, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharacterStats(10, 10, 10, 10, 31))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Difficulty(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Difficulty(41)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiceRoll(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiceRoll(21)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillCheck(StatType.WILL, new Difficulty(10), 21))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private record FixedRandomSource(int value) implements RandomSource {
        @Override
        public int nextIntInclusive(int minInclusive, int maxInclusive) {
            if (value < minInclusive || value > maxInclusive) {
                throw new IllegalArgumentException("고정 랜덤 값이 요청 범위를 벗어났습니다.");
            }
            return value;
        }
    }

    private static final class SequenceRandomSource implements RandomSource {
        private final Deque<Integer> values = new ArrayDeque<>();

        private SequenceRandomSource(int... values) {
            for (int value : values) {
                this.values.addLast(value);
            }
        }

        @Override
        public int nextIntInclusive(int minInclusive, int maxInclusive) {
            Integer value = values.pollFirst();
            if (value == null) {
                throw new IllegalStateException("테스트 랜덤 sequence가 소진되었습니다.");
            }
            if (value < minInclusive || value > maxInclusive) {
                throw new IllegalArgumentException("sequence 랜덤 값이 요청 범위를 벗어났습니다.");
            }
            return value;
        }
    }
}
