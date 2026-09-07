package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.InventoryCommand;
import com.uctale.uctale.domain.game.ItemDefinition;
import com.uctale.uctale.domain.game.ItemOwnershipType;
import com.uctale.uctale.domain.game.OwnedItem;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTurnCommitInventoryTest {

    @Test
    @DisplayName("GameTurnCommit은 inventory가 변했는데 audit이 누락되거나 변조되면 거절한다")
    void commit_RequiresStateChangesThatReplayToNextInventory() {
        GameState state = GameState.initial("세계관", "캐릭터", "오프닝");
        PlayerAction action = new PlayerAction(
                1, "token", ActionType.NARRATIVE_CHOICE, 1, Map.of("choiceId", "1"), "상자를 연다"
        );
        OwnedItem potion = new OwnedItem(
                "stack:potion", new ItemDefinition("potion", ItemOwnershipType.STACK, null), 1
        );
        TurnResolution resolution = new ActionResolver().resolveWithInventory(
                state, action, List.of(new InventoryCommand.Acquire(potion))
        );
        StateTransition committed = resolution.attachNarrative("상자에서 물약을 얻었다.");

        assertThatThrownBy(() -> new GameTurnCommit(
                1, 1, "상자를 연다", committed, "상자에서 물약을 얻었다.", "[]",
                "game-result:1", "story:1", null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inventory audit");
    }
}
