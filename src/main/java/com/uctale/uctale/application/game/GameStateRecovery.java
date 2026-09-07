package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.InventoryRules;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameStateRecovery {

    private final InventoryAuditCodec inventoryAuditCodec;

    public GameStateRecovery(InventoryAuditCodec inventoryAuditCodec) {
        this.inventoryAuditCodec = inventoryAuditCodec;
    }

    public GameState recover(GameSession session, List<GameLog> logs) {
        if (logs.isEmpty()) {
            throw new IllegalStateException("GameState를 복구할 게임 로그가 없습니다.");
        }

        GameLog opening = logs.getFirst();
        if (opening.getTurnNumber() != 1 || opening.getPreviousStateVersion() != 0 || opening.getStateVersion() != 1) {
            throw new IllegalStateException("Opening GameLog의 state transition이 올바르지 않습니다.");
        }
        if (!inventoryAuditCodec.deserialize(opening.getInventoryChangesJson()).isEmpty()) {
            throw new IllegalStateException("Opening GameLog에는 inventory state change가 있을 수 없습니다.");
        }

        GameState state = GameState.initial(
                session.getWorldSetting(),
                session.getCharacterSetting(),
                opening.getStoryText()
        );
        for (int i = 1; i < logs.size(); i++) {
            GameLog log = logs.get(i);
            if (log.getTurnNumber() != state.turnNumber() + 1
                    || log.getPreviousStateVersion() != state.turnNumber()
                    || log.getStateVersion() != state.turnNumber() + 1
                    || log.getInputChoiceText() == null
                    || log.getInputChoiceText().isBlank()) {
                throw new IllegalStateException("GameLog state transition을 복구할 수 없습니다.");
            }
            state = state.withInventory(InventoryRules.replay(
                    state.inventory(),
                    inventoryAuditCodec.deserialize(log.getInventoryChangesJson())
            ));
            state = state.advance(log.getInputChoiceText(), log.getStoryText());
        }
        return state;
    }
}
