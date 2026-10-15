package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.game.TurnProcessor;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.CombatEncounterStatus;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.GameplayActionRules;
import com.uctale.uctale.domain.game.QuestStatus;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresGameplayBridgeTest extends PostgresIntegrationTestSupport {
    private static final String OWNER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired private GamePersistenceService persistence;
    @Autowired private JdbcTemplate jdbc;
    private final TurnProcessor processor = new TurnProcessor();

    @BeforeEach
    void clear() {
        jdbc.execute("truncate table game_turn_reservation, game_mutation_request, image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    void gameplayProposalsPersistAsAuditableCanonicalStateAndRecoverWithoutSnapshot() {
        GameSession session = persistence.saveOpening(OWNER, "세계", "인물", "시작", "[]", null);
        play(session.getId(), 1, gameplay(1, "START_QUEST", "herb"));
        GameTurnCommit collected = play(session.getId(), 2, gameplay(2, "COLLECT", "herb"));
        assertThatThrownBy(() -> persistence.saveNextTurn(OWNER, session.getId(), collected))
                .isInstanceOf(TurnConflictException.class);
        play(session.getId(), 3, gameplay(3, "START_COMBAT", "wolf"));

        GameState combat = persistence.loadLatestTurn(OWNER, session.getId(), 4).gameState();
        String encounter = combat.combatEncounter().encounterId();
        play(session.getId(), 4, new PlayerAction(1, "pass", ActionType.COMBAT_PASS, 4,
                Map.of("encounterId", encounter), "대기"));
        play(session.getId(), 5, new PlayerAction(1, "escape", ActionType.COMBAT_ESCAPE, 5,
                Map.of("encounterId", encounter), "이탈"));
        play(session.getId(), 6, gameplay(6, "TALK", "guide"));

        GameState expected = persistence.loadLatestTurn(OWNER, session.getId(), 7).gameState();
        assertThat(expected.questState().quests().get("quest.v2.herb").status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(expected.questState().eventFlags()).containsKey("quest.completed.quest.v2.herb");
        assertThat(expected.inventory().requireItem("item:herb").quantity()).isEqualTo(1);
        assertThat(expected.combatEncounter().status()).isEqualTo(CombatEncounterStatus.ESCAPED);
        assertThat(expected.playerCharacter().vitals().hp().current()).isEqualTo(9);
        assertThat(expected.relationshipState().find("npc:guide").affinity()).isEqualTo(1);

        jdbc.update("delete from game_state_snapshot where session_id = ?", session.getId());
        assertThat(persistence.loadLatestTurn(OWNER, session.getId(), 7).gameState()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select quest_changes_json from game_log where session_id = ? and turn_number = 3",
                String.class, session.getId())).contains("FLAG_CHANGED");
        assertThat(jdbc.queryForObject("select combat_changes_json from game_log where session_id = ? and turn_number = 5",
                String.class, session.getId())).contains("ACTOR_ADVANCED");
    }

    private GameTurnCommit play(Long sessionId, int expectedTurn, PlayerAction action) {
        GameState state = persistence.loadLatestTurn(OWNER, sessionId, expectedTurn).gameState();
        TurnResolution result = processor.resolve(state, action, null, null);
        String story = "다음 장면 " + expectedTurn;
        GameTurnCommit commit = new GameTurnCommit(expectedTurn, action.legacyChoiceId(),
                action.displayText(), result.attachNarrative(story), story, "[]",
                "result-" + expectedTurn, "story-" + expectedTurn,
                result.gameResult().skillCheckResult(), result.gameResult().stateChanges(), null);
        persistence.saveNextTurn(OWNER, sessionId, commit);
        return commit;
    }

    private PlayerAction gameplay(int turn, String intent, String subject) {
        return new PlayerAction(1, "token-" + turn, ActionType.GAMEPLAY, turn,
                GameplayActionRules.issueArguments(1, intent, subject), intent + " " + subject);
    }
}
