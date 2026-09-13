package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionResolverVitalsTest {

    private final ActionResolver resolver = new ActionResolver();

    @Test
    @DisplayName("서버 vitals command만 HP를 변경하고 GameResult에 원인과 delta를 남긴다")
    void resolveWithVitals_AppliesCanonicalDamageOnce() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = choice(1, "위험을 감수한다");

        TurnResolution resolution = resolver.resolveWithVitals(state, action, List.of(new VitalsCommand.Damage(4)));

        assertThat(state.playerCharacter().vitals().hp().current()).isEqualTo(10);
        assertThat(resolution.stateTransition().nextState().playerCharacter().vitals().hp().current()).isEqualTo(6);
        assertThat(resolution.gameResult().stateChanges()).contains(
                new GameResult.VitalsChanged(GameResult.VitalResource.HP, 10, 6, -4, GameResult.VitalsChangeReason.DAMAGE));
    }

    @Test
    @DisplayName("narrative choice text만으로 vitals는 변경되지 않는다")
    void narrativeChoice_AloneCannotMutateVitals() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        TurnResolution resolution = resolver.resolve(state, choice(1, "HP가 1이 되었다고 말한다"));
        assertThat(resolution.stateTransition().nextState().playerCharacter().vitals()).isEqualTo(state.playerCharacter().vitals());
    }

    @Test
    @DisplayName("resolver가 END_OF_TURN status timing을 소유하고 기존 효과를 정확히 한 번 감소시킨다")
    void resolver_OwnsStatusTiming() {
        StatusEffect poison = new StatusEffect("poison", 1, 1, 2, StatusExpiryTrigger.END_OF_TURN, false);
        CharacterVitals withPoison = new VitalsRules().apply(CharacterVitals.defaults(),
                List.of(new VitalsCommand.ApplyStatus(poison))).vitals();
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝").withPlayerVitals(withPoison);

        TurnResolution resolution = resolver.resolve(state, choice(1, "기다린다"));
        assertThat(resolution.stateTransition().nextState().playerCharacter().vitals()
                .requireStatus("poison").remainingTurns()).isEqualTo(1);
        assertThat(resolution.gameResult().stateChanges()).contains(new GameResult.StatusDurationChanged("poison", 2, 1));
    }

    @Test
    @DisplayName("호출자가 status timing command를 주입할 수 없다")
    void resolver_RejectsInjectedTimingCommand() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        assertThatThrownBy(() -> resolver.resolveWithVitals(state, choice(1, "기다린다"), List.of(
                new VitalsCommand.AdvanceStatusDurations(StatusExpiryTrigger.END_OF_TURN)
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ActionResolver");
    }

    private PlayerAction choice(int sourceTurn, String text) {
        return new PlayerAction(1, "token", ActionType.NARRATIVE_CHOICE, sourceTurn,
                Map.of("choiceId", "1"), text);
    }
}
