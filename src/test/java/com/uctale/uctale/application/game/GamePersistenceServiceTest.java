package com.uctale.uctale.application.game;

import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import com.uctale.uctale.repository.ImageAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
                OWNER_A,
                "세계관",
                "캐릭터",
                "첫 이야기",
                "[{\"id\":1,\"text\":\"진행\"}]",
                null
        );

        assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getOwnerKey()).isEqualTo(OWNER_A);
        assertThat(gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1).sessionId()).isEqualTo(session.getId());

        assertThatThrownBy(() -> gamePersistenceService.loadLatestTurn(OWNER_B, session.getId(), 1))
                .isInstanceOf(GameSessionNotFoundException.class);
        assertThatThrownBy(() -> gamePersistenceService.saveNextTurn(
                OWNER_B, session.getId(), 1, "진행", "침입", "[]", null
        )).isInstanceOf(GameSessionNotFoundException.class);

        int savedTurn = gamePersistenceService.saveNextTurn(
                OWNER_A,
                session.getId(),
                1,
                "진행",
                "두 번째 이야기",
                "[{\"id\":1,\"text\":\"계속\"}]",
                null
        );

        assertThat(savedTurn).isEqualTo(2);
        assertThat(gameLogRepository.count()).isEqualTo(2);
        assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getCurrentTurn()).isEqualTo(2);
    }

    @Test
    @DisplayName("image asset은 세션과 turn에 연결되고 GameLog에는 서버 발급 URL만 저장된다")
    void imageAsset_IsPersistedWithSessionAndTurn() {
        ImageAssetService.AssetReference reference = new ImageAssetService.AssetReference(
                "11111111-1111-1111-1111-111111111111",
                "/api/game/image-assets/11111111-1111-1111-1111-111111111111",
                "dark street, zombie",
                "16:9"
        );

        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[]", reference
        );

        ImageAsset asset = imageAssetRepository.findById(reference.id()).orElseThrow();
        assertThat(asset.getGameSession().getId()).isEqualTo(session.getId());
        assertThat(asset.getTurnNumber()).isEqualTo(1);
        assertThat(asset.getPrompt()).isEqualTo("dark street, zombie");
        assertThat(gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1).imageUrl())
                .isEqualTo(reference.publicUrl());
    }

    @Test
    @DisplayName("스냅샷이 없는 동일 owner 세션은 저장된 로그에서 GameState를 복구한다")
    void loadLatestTurn_RecoversLegacySnapshotForOwnedSession() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[]", null
        );
        gamePersistenceService.saveNextTurn(
                OWNER_A, session.getId(), 1, "진행", "두 번째 이야기", "[]", null
        );
        gameStateSnapshotRepository.deleteById(session.getId());
        gameStateSnapshotRepository.flush();

        GamePersistenceService.LoadedTurn loaded = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 2);

        assertThat(loaded.gameState().turnNumber()).isEqualTo(2);
        assertThat(loaded.gameState().storyMemory().recentTurns()).hasSize(2);
    }

    @Test
    @DisplayName("동일 owner의 최신 턴 snapshot은 세션과 로그 상태가 일치한다")
    void loadLatestTurn_ReturnsConsistentSnapshot() {
        GameSession session = gamePersistenceService.saveOpening(
                OWNER_A, "세계관", "캐릭터", "첫 이야기", "[]", null
        );

        GamePersistenceService.LoadedTurn loaded = gamePersistenceService.loadLatestTurn(OWNER_A, session.getId(), 1);

        assertThat(loaded.sessionId()).isEqualTo(session.getId());
        assertThat(loaded.turnNumber()).isEqualTo(1);
        assertThat(loaded.storyText()).isEqualTo("첫 이야기");
        assertThat(loaded.gameState().storyMemory().canonicalFacts()).hasSize(2);
    }
}
