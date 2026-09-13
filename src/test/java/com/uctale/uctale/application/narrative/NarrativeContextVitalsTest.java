package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.domain.game.VitalsCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeContextVitalsTest {

    @Test
    @DisplayName("NarrativeContext는 서버 확정 vitals와 defeated/incapacitated 파생 상태를 projection한다")
    void projection_UsesCanonicalVitals() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = new PlayerAction(1, "token", ActionType.NARRATIVE_CHOICE, 1,
                Map.of("choiceId", "1"), "치명상을 입는다");
        TurnResolution resolution = new ActionResolver().resolveWithVitals(
                state, action, List.of(new VitalsCommand.Damage(999)));

        NarrativeContext context = NarrativeContext.from("result-2", resolution);
        assertThat(context.state().playerVitals().hp().current()).isZero();
        assertThat(context.state().defeated()).isTrue();
        assertThat(context.state().incapacitated()).isTrue();
        assertThat(context.stateChanges()).isEqualTo(resolution.gameResult().stateChanges());
    }
}
