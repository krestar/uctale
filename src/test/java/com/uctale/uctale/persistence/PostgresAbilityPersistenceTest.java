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
class PostgresAbilityPersistenceTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired private GamePersistenceService persistenceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    @DisplayName("ability 비용·효과·cooldown은 한 번 저장되고 snapshotless resume과 duplicate commit에서 일관된다")
    void abilityTransition_PersistsAndRecoversWithoutDoubleApply() {
        GameSession session = persistenceService.saveOpening(OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null);
        GameState initial = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        EnemyState enemy = new EnemyState("wolf", "늑대", CharacterVitals.defaults());
        CombatRules.Result started = CombatRules.start(initial.combatEncounter(), "enc-1", List.of(enemy));
        CombatRules.Result activated = CombatRules.activate(started.encounter(), initial.playerCharacter().vitals());
        GameState active = initial.withCombatEncounter(activated.encounter());
        StateTransition startTransition = new StateTransition(initial,
                active.advanceTurn().recordNarrativeTurn("전투 시작", "늑대가 달려든다."));
        GameTurnCommit startCommit = new GameTurnCommit(1, 1, "전투 시작", startTransition, "늑대가 달려든다.", "[]",
                "result-2", "story-2", null,
                List.of(started.stateChanges().getFirst(), activated.stateChanges().getFirst(), new GameResult.TurnAdvanced(1, 2)), null);
        persistenceService.saveNextTurn(OWNER_KEY, session.getId(), startCommit);

        GameState loaded = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        PlayerAction ability = new PlayerAction(2, "token", ActionType.COMBAT_ABILITY, 2,
                Map.of("encounterId", "enc-1", "abilityDefinitionId", "arcane-bolt", "targetId", "wolf"), "마법탄을 쏜다");
        TurnResolution resolution = new ActionResolver().resolve(loaded, ability);
        StateTransition transition = resolution.attachNarrative("마법탄이 늑대를 강타한다.");
        GameTurnCommit commit = new GameTurnCommit(2, 2, ability.displayText(), transition, "마법탄이 늑대를 강타한다.", "[]",
                "result-3", "story-3", null, resolution.gameResult().stateChanges(), null);

        assertThat(persistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit)).isEqualTo(3);
        assertThatThrownBy(() -> persistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit))
                .isInstanceOf(TurnConflictException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select combat_changes_json from game_log where session_id = ? and turn_number = 3",
                String.class, session.getId())).contains("ABILITY_RESOLVED").contains("ABILITY_COOLDOWN_CHANGED");

        jdbcTemplate.update("delete from game_state_snapshot where session_id = ?", session.getId());
        GameState recovered = persistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 3).gameState();
        assertThat(recovered).isEqualTo(transition.nextState());
        assertThat(recovered.playerCharacter().vitals().mp().current()).isEqualTo(7);
        assertThat(recovered.combatEncounter().requireEnemy("wolf").vitals().hp().current()).isEqualTo(6);
        assertThat(recovered.abilityState().remainingCooldown("arcane-bolt")).isEqualTo(2);
    }
}
