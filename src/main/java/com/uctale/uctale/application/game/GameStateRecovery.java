package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.AbilityRules;
import com.uctale.uctale.domain.game.CombatRules;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.InventoryRules;
import com.uctale.uctale.domain.game.QuestRules;
import com.uctale.uctale.domain.game.VitalsRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class GameStateRecovery {

    private final InventoryAuditCodec inventoryAuditCodec;
    private final VitalsAuditCodec vitalsAuditCodec;
    private final CombatAuditCodec combatAuditCodec;
    private final QuestAuditCodec questAuditCodec;

    @Autowired
    public GameStateRecovery(InventoryAuditCodec inventoryAuditCodec, VitalsAuditCodec vitalsAuditCodec,
            CombatAuditCodec combatAuditCodec, QuestAuditCodec questAuditCodec) {
        this.inventoryAuditCodec = inventoryAuditCodec;
        this.vitalsAuditCodec = vitalsAuditCodec;
        this.combatAuditCodec = combatAuditCodec;
        this.questAuditCodec = questAuditCodec;
    }

    public GameStateRecovery(InventoryAuditCodec inventoryAuditCodec, VitalsAuditCodec vitalsAuditCodec,
            CombatAuditCodec combatAuditCodec) {
        this(inventoryAuditCodec, vitalsAuditCodec, combatAuditCodec, new QuestAuditCodec(new ObjectMapper()));
    }

    public GameState recover(GameSession session, List<GameLog> logs) {
        if (logs.isEmpty()) throw new IllegalStateException("GameState를 복구할 게임 로그가 없습니다.");
        GameLog opening = logs.getFirst();
        if (opening.getTurnNumber() != 1 || opening.getPreviousStateVersion() != 0 || opening.getStateVersion() != 1) {
            throw new IllegalStateException("Opening GameLog의 state transition이 올바르지 않습니다.");
        }
        if (!inventoryAuditCodec.deserialize(opening.getInventoryChangesJson()).isEmpty()) throw new IllegalStateException("Opening GameLog에는 inventory state change가 있을 수 없습니다.");
        if (!vitalsAuditCodec.deserialize(opening.getVitalsChangesJson()).isEmpty()) throw new IllegalStateException("Opening GameLog에는 vitals/status state change가 있을 수 없습니다.");
        if (!combatAuditCodec.deserialize(opening.getCombatChangesJson()).isEmpty()) throw new IllegalStateException("Opening GameLog에는 combat/ability state change가 있을 수 없습니다.");
        if (!questAuditCodec.deserialize(opening.getQuestChangesJson()).isEmpty()) throw new IllegalStateException("Opening GameLog에는 quest/flag state change가 있을 수 없습니다.");

        GameState state = GameState.initial(session.getWorldSetting(), session.getCharacterSetting(), opening.getStoryText());
        for (int i = 1; i < logs.size(); i++) {
            GameLog log = logs.get(i);
            if (log.getTurnNumber() != state.turnNumber() + 1 || log.getPreviousStateVersion() != state.turnNumber()
                    || log.getStateVersion() != state.turnNumber() + 1 || log.getInputChoiceText() == null
                    || log.getInputChoiceText().isBlank()) throw new IllegalStateException("GameLog state transition을 복구할 수 없습니다.");
            var inventoryChanges = inventoryAuditCodec.deserialize(log.getInventoryChangesJson());
            var vitalsChanges = vitalsAuditCodec.deserialize(log.getVitalsChangesJson());
            var combatChanges = combatAuditCodec.deserialize(log.getCombatChangesJson());
            var questChanges = questAuditCodec.deserialize(log.getQuestChangesJson());
            var nextInventory = InventoryRules.replay(state.inventory(), inventoryChanges);
            var nextVitals = VitalsRules.replay(state.playerCharacter().vitals(), vitalsChanges);
            var nextCombat = CombatRules.replay(state.combatEncounter(), combatChanges, nextVitals);
            var nextAbilityState = AbilityRules.replay(state.abilityState(), combatChanges);
            var nextQuestState = QuestRules.replay(state.questState(), questChanges);
            state = state.withRuleState(nextInventory, nextVitals, nextCombat, nextAbilityState, nextQuestState)
                    .advance(log.getInputChoiceText(), log.getStoryText());
        }
        return state;
    }
}
