package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.game.TurnProcessor;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.InventoryCommand;
import com.uctale.uctale.domain.game.ItemDefinition;
import com.uctale.uctale.domain.game.ItemOwnershipType;
import com.uctale.uctale.domain.game.ObjectiveProgress;
import com.uctale.uctale.domain.game.OwnedItem;
import com.uctale.uctale.domain.game.QuestDefinitions;
import com.uctale.uctale.domain.game.QuestRules;
import com.uctale.uctale.domain.game.QuestStatus;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresQuestPersistenceTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired private GamePersistenceService persistenceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    @DisplayName("quest activation과 objective/flag audit은 한 번 commit되고 snapshot 없이 동일하게 복구된다")
    void questTransitions_PersistAndRecoverWithoutDoubleApply() {
        GameSession session = persistenceService.saveOpening(OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null);
        GameState initial = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        QuestRules rules = new QuestRules();
        QuestRules.Result activation = rules.activate(initial.questState(), QuestDefinitions.FIXTURE_QUEST_ID);
        GameState activated = initial.withQuestState(activation.questState()).advanceTurn().recordNarrativeTurn("퀘스트 시작", "여정이 시작됐다.");
        List<GameResult.StateChange> activationChanges = new ArrayList<>(activation.stateChanges());
        activationChanges.add(new GameResult.TurnAdvanced(1, 2));
        GameTurnCommit activationCommit = new GameTurnCommit(1, 1, "퀘스트 시작",
                new StateTransition(initial, activated), "여정이 시작됐다.", "[]", "result-2", "story-2",
                null, activationChanges, null);
        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), activationCommit)).isEqualTo(2);

        GameState active = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        TurnProcessor processor = new TurnProcessor();
        ItemDefinition supplyDefinition = new ItemDefinition(QuestDefinitions.COLLECTION_ITEM_DEFINITION_ID,
                ItemOwnershipType.STACK, null);
        PlayerAction collect = choice(9, 2, "보급품을 챙긴다");
        TurnResolution collection = processor.resolveWithInventory(active, collect,
                List.of(new InventoryCommand.Acquire(new OwnedItem("supply-stack", supplyDefinition, 2))));
        StateTransition collectionTransition = collection.attachNarrative("보급품을 확보했다.");
        GameTurnCommit collectionCommit = new GameTurnCommit(2, 9, collect.displayText(), collectionTransition,
                "보급품을 확보했다.", "[]", "result-3", "story-3", null,
                collection.gameResult().stateChanges(), null);
        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), collectionCommit)).isEqualTo(3);
        assertThatThrownBy(() -> persistenceService.saveNextTurn(OWNER_KEY, session.getId(), collectionCommit))
                .isInstanceOf(TurnConflictException.class);

        GameState afterCollection = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 3).gameState();
        PlayerAction dialogue = choice(QuestDefinitions.DIALOGUE_CHOICE_ID, 3, "안내자와 대화한다");
        TurnResolution spoken = processor.resolve(afterCollection, dialogue);
        StateTransition dialogueTransition = spoken.attachNarrative("안내자가 길을 알려줬다.");
        GameTurnCommit dialogueCommit = new GameTurnCommit(3, QuestDefinitions.DIALOGUE_CHOICE_ID, dialogue.displayText(),
                dialogueTransition, "안내자가 길을 알려줬다.", "[]", "result-4", "story-4", null,
                spoken.gameResult().stateChanges(), null);
        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), dialogueCommit)).isEqualTo(4);

        String audit = jdbcTemplate.queryForObject(
                "select quest_changes_json from game_log where session_id = ? and turn_number = 3",
                String.class, session.getId());
        assertThat(audit).contains("OBJECTIVE_PROGRESS_CHANGED").contains("FLAG_CHANGED").contains("COLLECTION");

        GameState expected = dialogueTransition.nextState();
        assertThat(expected.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID).status()).isEqualTo(QuestStatus.ACTIVE);
        assertThat(expected.questState().quests().get(QuestDefinitions.FIXTURE_QUEST_ID).objectiveProgress()
                .get(QuestDefinitions.COLLECT_OBJECTIVE_ID)).isEqualTo(ObjectiveProgress.count(2));
        assertThat(expected.questState().eventFlags()).isNotEmpty();
        assertThat(expected.questState().worldFlags()).isNotEmpty();

        jdbcTemplate.update("delete from game_state_snapshot where session_id = ?", session.getId());
        GameState recovered = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 4).gameState();
        assertThat(recovered).isEqualTo(expected);
    }

    private PlayerAction choice(int id, int sourceTurn, String text) {
        return new PlayerAction(id, "token", ActionType.NARRATIVE_CHOICE, sourceTurn,
                Map.of("choiceId", Integer.toString(id)), text);
    }
}
