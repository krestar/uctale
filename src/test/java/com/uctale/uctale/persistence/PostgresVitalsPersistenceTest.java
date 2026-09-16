package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.StatusEffect;
import com.uctale.uctale.domain.game.StatusExpiryTrigger;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.domain.game.VitalsCommand;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresVitalsPersistenceTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired private GamePersistenceService persistenceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() { jdbcTemplate.execute("truncate table image_asset, game_state_snapshot, game_log, game_session restart identity cascade"); }

    @Test
    @DisplayName("vitals/status transition은 snapshot과 GameLog audit에 저장되고 retry 및 snapshotless recovery에서도 한 번만 적용된다")
    void vitalsTransition_PersistsAndRecoversWithoutDoubleApply() throws Exception {
        GameSession session = persistenceService.saveOpening(OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null);
        GameState previous = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        StatusEffect poison = new StatusEffect("poison", 1, 2, 2, StatusExpiryTrigger.END_OF_TURN, false);
        ActionResolver resolver = new ActionResolver();
        TurnResolution resolution = resolver.resolveWithVitals(previous, choice(1, "함정을 통과한다"), List.of(new VitalsCommand.Damage(4), new VitalsCommand.ApplyStatus(poison)));
        StateTransition transition = resolution.attachNarrative("상처에서 피가 흐르고 독이 퍼진다.");
        GameTurnCommit commit = new GameTurnCommit(1, 1, "함정을 통과한다", transition, "상처에서 피가 흐르고 독이 퍼진다.", "[]",
                "result-2", "story-2", null, resolution.gameResult().stateChanges(), null);
        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit)).isEqualTo(2);
        String auditJson = jdbcTemplate.queryForObject("select vitals_changes_json from game_log where session_id = ? and turn_number = 2", String.class, session.getId());
        assertThat(auditJson).contains("VITALS_CHANGED").contains("STATUS_EFFECT_APPLIED");
        JsonNode snapshot = objectMapper.readTree(jdbcTemplate.queryForObject("select state_json from game_state_snapshot where session_id = ?", String.class, session.getId()));
        assertThat(snapshot.get("schemaVersion").asInt()).isEqualTo(10);
        assertThat(snapshot.get("state").get("playerCharacter").get("vitals").get("hp").get("current").asInt()).isEqualTo(6);
        assertThatThrownBy(() -> persistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit)).isInstanceOf(TurnConflictException.class);
        jdbcTemplate.update("delete from game_state_snapshot where session_id = ?", session.getId());
        GameState recovered = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        assertThat(recovered).isEqualTo(transition.nextState());
    }

    private PlayerAction choice(int sourceTurn, String text) {
        return new PlayerAction(1, "token", ActionType.NARRATIVE_CHOICE, sourceTurn, Map.of("choiceId", "1"), text);
    }
}
