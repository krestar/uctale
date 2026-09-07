package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionResolverInventoryTest {
    private final ActionResolver resolver = new ActionResolver();

    @Test
    @DisplayName("서버가 전달한 inventory command만 canonical next state와 GameResult를 변경한다")
    void resolve_AppliesServerInventoryCommandsBeforeNarrative() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = new PlayerAction(1, "token", ActionType.NARRATIVE_CHOICE, 1,
                Map.of("choiceId", "1"), "상자를 연다");
        OwnedItem potion = new OwnedItem("stack:potion",
                new ItemDefinition("potion", ItemOwnershipType.STACK, null), 2);

        TurnResolution withoutServerEffect = resolver.resolve(state, action);
        TurnResolution withServerEffect = resolver.resolveWithInventory(
                state, action, List.of(new InventoryCommand.Acquire(potion)));

        assertThat(withoutServerEffect.stateTransition().nextState().inventory()).isEqualTo(Inventory.empty());
        assertThat(withServerEffect.stateTransition().nextState().inventory().requireItem("stack:potion").quantity())
                .isEqualTo(2);
        assertThat(withServerEffect.gameResult().stateChanges()).containsExactly(
                new GameResult.ItemAcquired(potion, 2),
                new GameResult.TurnAdvanced(1, 2));
    }
}
