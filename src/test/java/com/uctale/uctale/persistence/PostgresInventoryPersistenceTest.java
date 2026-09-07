package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.EquipmentSlot;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.InventoryCommand;
import com.uctale.uctale.domain.game.ItemDefinition;
import com.uctale.uctale.domain.game.ItemOwnershipType;
import com.uctale.uctale.domain.game.OwnedItem;
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

@SpringBootTest
class PostgresInventoryPersistenceTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired private GamePersistenceService gamePersistenceService;
    @Autowired private GameMutationRequestService mutationRequestService;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final ActionResolver resolver = new ActionResolver();

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table game_turn_reservation, game_mutation_request, image_asset, game_state_snapshot, game_log, game_session restart identity cascade");
    }

    @Test
    @DisplayName("inventory/equipment transition은 snapshot과 GameLog audit에 저장되고 snapshot 없이도 동일하게 복구된다")
    void inventoryTransition_PersistsAndRecoversFromLedger() {
        GameSession session = gamePersistenceService.saveOpening(OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null);
        GameState previous = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        OwnedItem sword = new OwnedItem("sword:001",
                new ItemDefinition("iron-sword", ItemOwnershipType.INSTANCE, EquipmentSlot.MAIN_HAND), 1);
        OwnedItem potion = new OwnedItem("stack:potion",
                new ItemDefinition("potion", ItemOwnershipType.STACK, null), 3);
        TurnResolution resolution = resolver.resolveWithInventory(previous, action(1, 1, "상자를 연다"), List.of(
                new InventoryCommand.Acquire(sword),
                new InventoryCommand.Acquire(potion),
                new InventoryCommand.Equip("sword:001", EquipmentSlot.MAIN_HAND)));
        StateTransition committed = resolution.attachNarrative("검과 물약을 챙겼다.");
        gamePersistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit(1, committed, resolution, "검과 물약을 챙겼다."));

        GameState loaded = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        assertThat(loaded).isEqualTo(committed.nextState());
        assertThat(loaded.inventory().requireItem("stack:potion").quantity()).isEqualTo(3);
        assertThat(loaded.inventory().equipment().itemIdAt(EquipmentSlot.MAIN_HAND)).isEqualTo("sword:001");
        String audit = jdbcTemplate.queryForObject(
                "select inventory_changes_json from game_log where session_id = ? and turn_number = 2",
                String.class, session.getId());
        assertThat(audit).contains("ITEM_ACQUIRED", "ITEM_EQUIPPED", "sword:001", "stack:potion");

        jdbcTemplate.update("delete from game_state_snapshot where session_id = ?", session.getId());
        GameState recovered = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        assertThat(recovered).isEqualTo(committed.nextState());
    }

    @Test
    @DisplayName("동일 idempotency request replay는 item 소비를 두 번 적용하지 않는다")
    void completedIdempotencyReplay_DoesNotConsumeTwice() {
        GameSession session = gamePersistenceService.saveOpening(OWNER_KEY, "세계관", "캐릭터", "오프닝", "[]", null);
        GameState opening = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 1).gameState();
        OwnedItem potion = new OwnedItem("stack:potion", new ItemDefinition("potion", ItemOwnershipType.STACK, null), 3);
        TurnResolution acquire = resolver.resolveWithInventory(opening, action(1, 1, "물약을 얻는다"),
                List.of(new InventoryCommand.Acquire(potion)));
        StateTransition acquired = acquire.attachNarrative("물약 세 개를 얻었다.");
        gamePersistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit(1, acquired, acquire, "물약 세 개를 얻었다."));

        String key = "inventory-consume-retry";
        String fingerprint = "consume-potion-once";
        GameMutationRequestService.BeginResult first = mutationRequestService.begin(
                OWNER_KEY, GameMutationRequestService.PROGRESS, key, session.getId(), 2, fingerprint);
        GameState beforeConsume = gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 2).gameState();
        TurnResolution consume = resolver.resolveWithInventory(beforeConsume, action(2, 1, "물약을 마신다"),
                List.of(new InventoryCommand.Consume("stack:potion", 1)));
        StateTransition consumed = consume.attachNarrative("물약 하나를 마셨다.");
        gamePersistenceService.saveNextTurn(OWNER_KEY, session.getId(), commit(2, consumed, consume, "물약 하나를 마셨다."),
                first.requestId(), "물약 사용", first.reservationOwner());

        GameMutationRequestService.BeginResult replay = mutationRequestService.begin(
                OWNER_KEY, GameMutationRequestService.PROGRESS, key, session.getId(), 2, fingerprint);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.resultTurn()).isEqualTo(3);
        assertThat(gamePersistenceService.loadLatestTurn(OWNER_KEY, session.getId(), 3)
                .gameState().inventory().requireItem("stack:potion").quantity()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_log where session_id = ? and turn_number = 3", Integer.class, session.getId()))
                .isEqualTo(1);
    }

    private GameTurnCommit commit(int expectedTurn, StateTransition transition, TurnResolution resolution, String storyText) {
        return new GameTurnCommit(expectedTurn, 1, resolution.gameResult().resolvedAction().displayText(), transition,
                storyText, "[]", null, null, resolution.gameResult().skillCheckResult(), resolution.gameResult().stateChanges(), null);
    }

    private PlayerAction action(int sourceTurn, int choiceId, String text) {
        return new PlayerAction(choiceId, "token", ActionType.NARRATIVE_CHOICE, sourceTurn,
                Map.of("choiceId", Integer.toString(choiceId)), text);
    }
}
