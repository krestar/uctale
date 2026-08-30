package com.uctale.uctale.application.game;

import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import com.uctale.uctale.repository.ImageAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class GamePersistenceServiceTest {

    private static final String OWNER_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OWNER_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    @Autowired private GamePersistenceService gamePersistenceService;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private GameLogRepository gameLogRepository;
    @Autowired private GameStateSnapshotRepository gameStateSnapshotRepository;
    @Autowired private ImageAssetRepository imageAssetRepository;

    @Test
    @DisplayName("새 게임 세션은 생성 owner에게 귀속되고 동일 owner만 조회·진행할 수 있다")
    void sessionOwnership_IsEnforcedAcrossReadAndWrite() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[{\"id\":1,\"text\":\"진행\"}]", null
        );
        GameState initialState = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1).gameState();
        GameTurnCommit commit = commit(initialState, 1, "진행", "두 번째 이야기", "[{\"id\":1,\"text\":\"계속\"}]");

        assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getOwnerKey()).isEqualTo(OWNER_A);
        assertThatThrownBy(() -> gamePersistenceService.loadLatestTurn(OWNER_B, session.getId(), 1))
                .isInstanceOf(GameSessionNotFoundException.class);
        assertThatThrownBy(() -> gamePersistenceService.saveNextTurn(OWNER_B, session.getId(), commit))
                .isInstanceOf(GameSessionNotFoundException.class);

        int savedTurn = gamePersistenceService.saveNextTurn(OWNER_A, session.getId(), commit);

        assertThat(savedTurn).isEqualTo(2);
        assertThat(gameLogRepository.count()).isEqualTo(2);
        assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getCurrentTurn()).isEqualTo(2);
    }

    @Test
    @DisplayName("새 완료 turn은 입력·state version·결과를 자기 행에 한 번에 기록한다")
    void saveNextTurn_AppendsSelfContainedCommittedTurn() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[{\"id\":7,\"text\":\"문을 연다\"}]", null
        );
        GameState initialState = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1).gameState();

        gamePersistenceService.saveNextTurn(
                OWNER_A,
                session.getId(),
                commit(initialState, 7, "문을 연다", "두 번째 이야기", "[{\"id\":3,\"text\":\"살핀다\"}]")
        );

        List<GameLog> logs = gameLogRepository.findByGameSessionOrderByTurnNumberAsc(session);
        assertThat(logs).hasSize(2);

        GameLog opening = logs.getFirst();
        assertThat(opening.getInputChoiceId()).isNull();
        assertThat(opening.getInputChoiceText()).isNull();
        assertThat(opening.getPreviousStateVersion()).isZero();
        assertThat(opening.getStateVersion()).isEqualTo(1);

        GameLog committed = logs.get(1);
        assertThat(committed.getInputChoiceId()).isEqualTo(7);
        assertThat(committed.getInputChoiceText()).isEqualTo("문을 연다");
        assertThat(committed.getPreviousStateVersion()).isEqualTo(1);
        assertThat(committed.getStateVersion()).isEqualTo(2);
        assertThat(committed.getStoryText()).isEqualTo("두 번째 이야기");
        assertThat(committed.getChoicesJson()).contains("살핀다");
        assertThat(committed.getCommittedAt()).isNotNull();
    }

    @Test
    @DisplayName("image asset은 생성 model/size/seed/safe/style을 turn과 함께 영속화한다")
    void imageAsset_IsPersistedWithFrozenGenerationContract() {
        ImageAssetService.AssetReference reference = new ImageAssetService.AssetReference(
                "11111111-1111-1111-1111-111111111111",
                "/api/game/image-assets/11111111-1111-1111-1111-111111111111",
                "dark street, zombie", "16:9", "flux", 1024, 576, 12345, true, "uctale-charcoal-v1"
        );

        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[]", reference
        );

        ImageAsset asset = imageAssetRepository.findById(reference.id()).orElseThrow();
        assertThat(asset.getGameSession().getId()).isEqualTo(session.getId());
        assertThat(asset.getTurnNumber()).isEqualTo(1);
        assertThat(asset.getPrompt()).isEqualTo("dark street, zombie");
        assertThat(asset.getModel()).isEqualTo("flux");
        assertThat(asset.getWidth()).isEqualTo(1024);
        assertThat(asset.getHeight()).isEqualTo(576);
        assertThat(asset.getSeed()).isEqualTo(12345);
        assertThat(asset.isSafe()).isTrue();
        assertThat(asset.getStyleVersion()).isEqualTo("uctale-charcoal-v1");
        assertThat(gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1).imageUrl())
                .isEqualTo(reference.publicUrl());
    }

    @Test
    @DisplayName("snapshot이 없으면 append-only 로그의 자기 행 입력으로 GameState를 복구한다")
    void loadLatestTurn_RecoversStateFromCommittedTurnLedger() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[]", null
        );
        GameState initialState = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1).gameState();
        gamePersistenceService.saveNextTurn(
                OWNER_A,
                session.getId(),
                commit(initialState, 1, "진행", "두 번째 이야기", "[]")
        );
        gameStateSnapshotRepository.deleteById(session.getId());
        gameStateSnapshotRepository.flush();

        GamePersistenceService.LoadedTurn loaded = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 2);

        assertThat(loaded.gameState().turnNumber()).isEqualTo(2);
        assertThat(loaded.gameState().storyMemory().recentTurns()).hasSize(2);
        assertThat(loaded.gameState().storyMemory().recentTurns().getLast().playerAction()).isEqualTo("진행");
    }

    @Test
    @DisplayName("동일 owner의 최신 턴 snapshot은 세션과 로그 state version이 일치한다")
    void loadLatestTurn_ReturnsConsistentSnapshot() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[]", null
        );

        GamePersistenceService.LoadedTurn loaded = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1);

        assertThat(loaded.sessionId()).isEqualTo(session.getId());
        assertThat(loaded.turnNumber()).isEqualTo(1);
        assertThat(loaded.storyText()).isEqualTo("첫 이야기");
        assertThat(loaded.gameState().storyMemory().canonicalFacts()).hasSize(2);
        assertThat(gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session).orElseThrow().getStateVersion())
                .isEqualTo(loaded.gameState().turnNumber());
    }

    private GameTurnCommit commit(
            GameState previousState,
            int choiceId,
            String choiceText,
            String storyText,
            String choicesJson
    ) {
        return new GameTurnCommit(
                previousState.turnNumber(),
                choiceId,
                choiceText,
                previousState,
                previousState.advance(choiceText, storyText),
                storyText,
                choicesJson,
                null
        );
    }
}
