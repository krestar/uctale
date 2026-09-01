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
        assertThat(resolution.gameResult().skillCheckResult()).isNull();
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
    @DisplayName("Skill Check는 fixed RandomSource로 성공 판정을 만들고 저장 결과를 그대로 GameResult에 사용한다")
    void resolveSkillCheck_Success() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = skillCheck(1, 7, 10, 0);

        SkillCheckResult rolled = resolver.rollSkillCheck(state, action, (min, max) -> 10);
        TurnResolution resolution = resolver.resolve(state, action, rolled);

        assertThat(rolled.rawRoll()).isEqualTo(10);
        assertThat(rolled.total()).isEqualTo(10);
        assertThat(rolled.outcome()).isEqualTo(SkillCheckOutcome.SUCCESS);
        assertThat(resolution.gameResult().skillCheckResult()).isEqualTo(rolled);
        assertThat(resolution.gameResult().events()).containsExactly(
                GameResult.GameEvent.ACTION_RESOLVED,
                GameResult.GameEvent.SKILL_CHECK_RESOLVED
        );
    }

    @Test
    @DisplayName("Skill Check는 total이 DC보다 하나 작으면 실패한다")
    void resolveSkillCheck_FailureBoundary() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = skillCheck(1, 7, 10, 0);

        SkillCheckResult rolled = resolver.rollSkillCheck(state, action, (min, max) -> 9);

        assertThat(rolled.total()).isEqualTo(9);
        assertThat(rolled.outcome()).isEqualTo(SkillCheckOutcome.FAILURE);
        assertThat(resolver.resolve(state, action, rolled).gameResult().skillCheckResult()).isEqualTo(rolled);
    }

    @Test
    @DisplayName("저장된 Skill Check의 stat modifier가 canonical 능력치와 다르면 거절한다")
    void resolveSkillCheck_RejectsStoredResultFromDifferentStats() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = skillCheck(1, 7, 10, 0);
        SkillCheckResult tampered = new SkillCheckResult(
                StatType.WILL, 10, 1, 0, 10, 11, SkillCheckOutcome.SUCCESS, SkillCheck.RULESET_VERSION
        );

        assertThatThrownBy(() -> resolver.resolve(state, action, tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical 능력치");
    }

    @Test
    @DisplayName("stale source turn과 잘못된 Skill Check arguments는 난수 생성 전에 거절한다")
    void resolveSkillCheck_RejectsBeforeRoll() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction stale = skillCheck(2, 7, 10, 0);

        assertThatThrownBy(() -> resolver.rollSkillCheck(state, stale, (min, max) -> 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source turn");

        PlayerAction invalid = new PlayerAction(
                7,
                "token",
                ActionType.SKILL_CHECK,
                1,
                Map.of("choiceId", "7", "statType", "WILL", "dc", "41", "situationalModifier", "0"),
                "시도한다"
        );
        assertThatThrownBy(() -> resolver.rollSkillCheck(state, invalid, (min, max) -> 10))
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

    private PlayerAction skillCheck(int sourceTurn, int choiceId, int dc, int situationalModifier) {
        return new PlayerAction(
                choiceId,
                "token",
                ActionType.SKILL_CHECK,
                sourceTurn,
                Map.of(
                        "choiceId", Integer.toString(choiceId),
                        "statType", "WILL",
                        "dc", Integer.toString(dc),
                        "situationalModifier", Integer.toString(situationalModifier)
                ),
                "시도한다"
        );
    }
}
