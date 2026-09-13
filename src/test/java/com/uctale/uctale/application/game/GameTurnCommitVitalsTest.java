package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.VitalsCommand;
import com.uctale.uctale.domain.game.VitalsRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTurnCommitVitalsTest {

    @Test
    @DisplayName("vitals가 바뀐 transition은 일치하는 audit 없이는 commit할 수 없다")
    void changedVitals_RequireMatchingAudit() {
        GameState previous = GameState.initial("세계관", "캐릭터", "오프닝");
        VitalsRules.Result result = new VitalsRules().apply(previous.playerCharacter().vitals(),
                List.of(new VitalsCommand.Damage(3)));
        GameState next = previous.withPlayerVitals(result.vitals()).advance("진행", "다쳤다");
        StateTransition transition = new StateTransition(previous, next);

        assertThatThrownBy(() -> new GameTurnCommit(
                1, 1, "진행", transition, "다쳤다", "[]", null, null, null, List.of(), null
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vitals/status audit");

        GameTurnCommit valid = new GameTurnCommit(
                1, 1, "진행", transition, "다쳤다", "[]", null, null, null,
                result.stateChanges(), null
        );
        assertThat(valid.stateChanges()).containsExactly(new GameResult.VitalsChanged(
                GameResult.VitalResource.HP, 10, 7, -3, GameResult.VitalsChangeReason.DAMAGE));
    }
}
