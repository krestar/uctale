package com.uctale.uctale.application.narrative;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.TurnResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeContextTest {

    @Test
    @DisplayName("NarrativeContext는 확정 GameResult와 canonical next state를 provider-safe projection으로 만든다")
    void from_UsesResolvedResultAndCanonicalNextState() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = new PlayerAction(
                7,
                "SERVER_ONLY_ACTION_TOKEN",
                ActionType.NARRATIVE_CHOICE,
                1,
                Map.of("choiceId", "7"),
                "문을 잠근다"
        );
        TurnResolution resolution = new ActionResolver().resolve(state, action);

        NarrativeContext context = NarrativeContext.from("game-result:42:2:100", resolution);

        assertThat(context.canonicalResultId()).isEqualTo("game-result:42:2:100");
        assertThat(context.outcome()).isEqualTo(GameResult.Outcome.RESOLVED);
        assertThat(context.resolvedAction().legacyChoiceId()).isEqualTo(7);
        assertThat(context.resolvedAction().displayText()).isEqualTo("문을 잠근다");
        assertThat(context.playerAction()).isEqualTo("문을 잠근다");
        assertThat(context.state().turnNumber()).isEqualTo(2);
        assertThat(context.state().worldPremise()).isEqualTo("세계관");
        assertThat(context.memory().recentTurns()).hasSize(1);
        assertThat(context.stateChanges()).containsExactly(new GameResult.TurnAdvanced(1, 2));
        assertThat(context.narrativeCues()).containsExactly("문을 잠근다");
        assertThat(context.forbiddenCanonicalMutations()).anyMatch(rule -> rule.contains("HP"));
    }

    @Test
    @DisplayName("canonical result link가 없으면 NarrativeContext를 만들 수 없다")
    void from_RejectsBlankCanonicalResultId() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = new PlayerAction(
                1, "token", ActionType.NARRATIVE_CHOICE, 1, Map.of("choiceId", "1"), "간다"
        );
        TurnResolution resolution = new ActionResolver().resolve(state, action);

        assertThatThrownBy(() -> NarrativeContext.from(" ", resolution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonicalResultId");
    }
}
