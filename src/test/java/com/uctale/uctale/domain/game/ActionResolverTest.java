package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionResolverTest {

    private final ActionResolver resolver = new ActionResolver();

    @Test
    @DisplayName("Narrative choice는 provider 없이 GameResult와 다음 canonical turn을 확정한다")
    void resolveNarrativeChoice_ProducesDeterministicResolution() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = narrativeChoice(1, 7, "문을 잠근다");

        TurnResolution resolution = resolver.resolve(state, action);

        assertThat(resolution.gameResult().resolvedAction()).isEqualTo(action);
        assertThat(resolution.gameResult().outcome()).isEqualTo(GameResult.Outcome.RESOLVED);
        assertThat(resolution.gameResult().canonicalFacts()).isEmpty();
        assertThat(resolution.gameResult().events()).containsExactly(GameResult.GameEvent.ACTION_RESOLVED);
        assertThat(resolution.gameResult().stateChanges())
                .containsExactly(new GameResult.TurnAdvanced(1, 2));
        assertThat(resolution.gameResult().narrativeCues()).containsExactly("문을 잠근다");
        assertThat(resolution.stateTransition().previousState()).isEqualTo(state);
        assertThat(resolution.stateTransition().nextState().turnNumber()).isEqualTo(2);
        assertThat(resolution.stateTransition().nextState().playerCharacter()).isEqualTo(state.playerCharacter());
        assertThat(resolution.stateTransition().nextState().worldState()).isEqualTo(state.worldState());
        assertThat(resolution.stateTransition().nextState().storyMemory()).isEqualTo(state.storyMemory());
    }

    @Test
    @DisplayName("같은 state와 action은 동일한 TurnResolution을 만든다")
    void resolveNarrativeChoice_IsDeterministic() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = narrativeChoice(1, 3, "기다린다");

        assertThat(resolver.resolve(state, action)).isEqualTo(resolver.resolve(state, action));
    }

    @Test
    @DisplayName("stale source turn은 규칙 실행 전에 거절한다")
    void resolveNarrativeChoice_RejectsStaleSourceTurn() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction stale = narrativeChoice(2, 1, "간다");

        assertThatThrownBy(() -> resolver.resolve(state, stale))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source turn");
    }

    @Test
    @DisplayName("choiceId arguments 변조는 resolver에서도 거절한다")
    void resolveNarrativeChoice_RejectsTamperedArguments() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction tampered = new PlayerAction(
                7, "token", ActionType.NARRATIVE_CHOICE, 1, Map.of("choiceId", "8"), "문을 잠근다"
        );

        assertThatThrownBy(() -> resolver.resolve(state, tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arguments");
    }

    @Test
    @DisplayName("Narrative 부착은 확정된 canonical 상태를 바꾸지 않고 StoryMemory만 완성한다")
    void attachNarrative_OnlyCompletesNarrativeMemory() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        TurnResolution resolution = resolver.resolve(state, narrativeChoice(1, 7, "문을 잠근다"));
        GameState canonicalNextState = resolution.stateTransition().nextState();

        StateTransition committed = resolution.attachNarrative("문은 굳게 잠겼다.");

        assertThat(committed.previousState()).isEqualTo(state);
        assertThat(committed.nextState().turnNumber()).isEqualTo(canonicalNextState.turnNumber());
        assertThat(committed.nextState().playerCharacter()).isEqualTo(canonicalNextState.playerCharacter());
        assertThat(committed.nextState().worldState()).isEqualTo(canonicalNextState.worldState());
        assertThat(committed.nextState().storyMemory().canonicalFacts())
                .isEqualTo(canonicalNextState.storyMemory().canonicalFacts());
        assertThat(committed.nextState().storyMemory().recentTurns().getLast())
                .isEqualTo(new GameTurn(2, "문을 잠근다", "문은 굳게 잠겼다."));
    }

    private PlayerAction narrativeChoice(int sourceTurn, int choiceId, String text) {
        return new PlayerAction(
                choiceId,
                "token",
                ActionType.NARRATIVE_CHOICE,
                sourceTurn,
                Map.of("choiceId", Integer.toString(choiceId)),
                text
        );
    }
}
