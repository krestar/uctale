package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.CharacterVitals;
import com.uctale.uctale.domain.game.CombatEncounter;
import com.uctale.uctale.domain.game.CombatEncounterStatus;
import com.uctale.uctale.domain.game.CombatRules;
import com.uctale.uctale.domain.game.EnemyState;
import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresCombatPersistenceTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired private GamePersistenceService persistenceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    @DisplayName("combat participant와 current actor는 snapshot/GameLog audit에 저장되고 retry/snapshotless resume에도 동일하다")
    void combatTransition_PersistsAndRecoversWithoutDoubleApply() {
        GameSession session = persistenceService.saveOpening(OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null);
        GameState initial = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        EnemyState enemy = new EnemyState("wolf", "늑대", CharacterVitals.defaults());
        CombatRules.Result started = CombatRules.start(initial.combatEncounter(), "enc-1", List.of(enemy));
        CombatRules.Result activated = CombatRules.activate(started.encounter(), initial.playerCharacter().vitals());
        GameResult.CombatEncounterChanged startChange = (GameResult.CombatEncounterChanged) started.stateChanges().getFirst();
        GameResult.CombatEncounterChanged activateChange = (GameResult.CombatEncounterChanged) activated.stateChanges().getFirst();
        GameState activeState = initial.withCombatEncounter(activated.encounter());
        StateTransition startTransition = new StateTransition(initial, activeState.advanceTurn().recordNarrativeTurn("전투 시작", "늑대가 달려든다."));
        GameTurnCommit startCommit = new GameTurnCommit(1, 1, "전투 시작", startTransition, "늑대가 달려든다.", "[]",
                "result-2", "story-2", null, List.of(startChange, activateChange, new GameResult.TurnAdvanced(1, 2)), null);
        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), startCommit)).isEqualTo(2);

        GameState loaded = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        assertThat(loaded.combatEncounter().status()).isEqualTo(CombatEncounterStatus.ACTIVE);
        assertThat(loaded.combatEncounter().enemies()).containsOnlyKeys("wolf");
        assertThat(loaded.combatEncounter().currentActorId()).isEqualTo("player");
        assertThatThrownBy(() -> persistenceService.saveNextTurn(OWNER_KEY, session.getId(), startCommit)).isInstanceOf(TurnConflictException.class);

        ActionResolver resolver = new ActionResolver();
        TurnResolution pass = resolver.resolve(loaded, new PlayerAction(1, "token", ActionType.COMBAT_PASS, 2,
                Map.of("encounterId", "enc-1"), "태세를 유지한다"));
        StateTransition passTransition = pass.attachNarrative("늑대의 차례가 된다.");
        GameTurnCommit passCommit = new GameTurnCommit(2, 1, "태세를 유지한다", passTransition, "늑대의 차례가 된다.", "[]",
                "result-3", "story-3", null, pass.gameResult().stateChanges(), null);
        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), passCommit)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select combat_changes_json from game_log where session_id = ? and turn_number = 3", String.class, session.getId()))
                .contains("ACTOR_ADVANCED");

        jdbcTemplate.update("delete from game_state_snapshot where session_id = ?", session.getId());
        GameState recovered = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 3).gameState();
        assertThat(recovered).isEqualTo(passTransition.nextState());
        assertThat(recovered.combatEncounter().currentActorId()).isEqualTo("wolf");
    }
}
