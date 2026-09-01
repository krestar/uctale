package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.SkillCheckOutcome;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StatType;
import com.uctale.uctale.dto.GameResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameResponseProjectorTest {

    @Test
    void projectsCanonicalStatsAndServerSkillCheckWithoutRecalculation() {
        GameState state = GameState.initial("세계", "캐릭터", "오프닝");
        SkillCheckResult result = new SkillCheckResult(
                StatType.WILL, 10, 0, 0, 10, 10, SkillCheckOutcome.SUCCESS, 1
        );

        GameResponse response = GameResponseProjector.project(
                42L, 2, "장면", "스토리", List.of(), null, state, result
        );

        assertThat(response.characterStats())
                .extracting(GameResponse.CharacterStat::key)
                .containsExactly("MIGHT", "AGILITY", "INTELLECT", "WILL", "PRESENCE");
        assertThat(response.characterStats())
                .allSatisfy(stat -> {
                    assertThat(stat.score()).isEqualTo(10);
                    assertThat(stat.modifier()).isZero();
                });
        assertThat(response.latestSkillCheck().statType()).isEqualTo("WILL");
        assertThat(response.latestSkillCheck().rawRoll()).isEqualTo(10);
        assertThat(response.latestSkillCheck().outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void replayUsesStoredAuditValuesWithoutConstructingDomainResult() {
        GamePersistenceService.SkillCheckAudit audit = new GamePersistenceService.SkillCheckAudit(
                "FUTURE_STAT", 20, 99, -99, 1, -123, "FUTURE_OUTCOME", 999
        );

        GameResponse response = GameResponseProjector.projectReplay(
                42L, 1, "장면", "스토리", List.of(), null,
                GameState.initial("세계", "캐릭터", "오프닝"), audit
        );

        assertThat(response.latestSkillCheck().statType()).isEqualTo("FUTURE_STAT");
        assertThat(response.latestSkillCheck().statModifier()).isEqualTo(99);
        assertThat(response.latestSkillCheck().total()).isEqualTo(-123);
        assertThat(response.latestSkillCheck().outcome()).isEqualTo("FUTURE_OUTCOME");
        assertThat(response.latestSkillCheck().rulesetVersion()).isEqualTo(999);
    }

    @Test
    void replayDoesNotUseNewerStateAsHistoricalStats() {
        GameState newerState = GameState.initial("세계", "캐릭터", "오프닝").advance("행동", "다음 이야기");

        GameResponse response = GameResponseProjector.projectReplay(
                42L, 1, "과거 장면", "오프닝", List.of(), null, newerState, null
        );

        assertThat(response.characterStats()).isEmpty();
    }

    @Test
    void replayRejectsPartialAuditInsteadOfSilentlyDefaulting() {
        GamePersistenceService.SkillCheckAudit partial = new GamePersistenceService.SkillCheckAudit(
                "WILL", 10, null, 0, 10, 10, "SUCCESS", 1
        );

        assertThatThrownBy(() -> GameResponseProjector.projectReplay(
                42L, 1, "장면", "스토리", List.of(), null,
                GameState.initial("세계", "캐릭터", "오프닝"), partial
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("불완전");
    }
}
