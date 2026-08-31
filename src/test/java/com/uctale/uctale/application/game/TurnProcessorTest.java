package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TurnProcessorTest {

    private final TurnProcessor turnProcessor = new TurnProcessor();

    @Test
    @DisplayName("application processor는 resolution을 먼저 만들고 narrative를 같은 transition에 부착한다")
    void resolveThenAttachNarrative_PreservesResolvedOutcome() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = new PlayerAction(
                4,
                "token",
                ActionType.NARRATIVE_CHOICE,
                1,
                Map.of("choiceId", "4"),
                "계단을 오른다"
        );

        TurnResolution resolution = turnProcessor.resolve(state, action);
        GameResult resolvedResult = resolution.gameResult();
        GameState canonicalNextState = resolution.stateTransition().nextState();

        StateTransition committed = turnProcessor.attachNarrative(resolution, "계단 끝에서 낡은 문을 발견했다.");

        assertThat(resolution.gameResult()).isSameAs(resolvedResult);
        assertThat(resolvedResult.outcome()).isEqualTo(GameResult.Outcome.RESOLVED);
        assertThat(committed.nextState().turnNumber()).isEqualTo(canonicalNextState.turnNumber());
        assertThat(committed.nextState().playerCharacter()).isEqualTo(canonicalNextState.playerCharacter());
        assertThat(committed.nextState().worldState()).isEqualTo(canonicalNextState.worldState());
        assertThat(committed.nextState().storyMemory().recentTurns().getLast().storyText())
                .isEqualTo("계단 끝에서 낡은 문을 발견했다.");
    }
}
