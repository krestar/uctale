package com.uctale.uctale.application.game;

import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GamePersistenceIntegrityTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private GameLogRepository gameLogRepository;
    @Mock private GameStateSnapshotRepository gameStateSnapshotRepository;
    @Mock private GameStateCodec gameStateCodec;

    private GamePersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new GamePersistenceService(
                gameSessionRepository,
                gameLogRepository,
                gameStateSnapshotRepository,
                gameStateCodec
        );
    }

    @Test
    @DisplayName("일반 DataIntegrityViolationException은 턴 충돌 409로 오인하지 않는다")
    void saveNextTurn_DoesNotMapEveryIntegrityFailureToTurnConflict() {
        given(gameSessionRepository.findByIdAndOwnerKey(42L, OWNER_KEY))
                .willThrow(new DataIntegrityViolationException("not-null constraint violation"));

        assertThatThrownBy(() -> persistenceService.saveNextTurn(
                OWNER_KEY, 42L, 1, "선택", "본문", "[]", null
        )).isInstanceOf(PersistenceOperationException.class)
                .isNotInstanceOf(TurnConflictException.class);
    }

    @Test
    @DisplayName("턴 unique constraint 위반만 턴 충돌로 분류한다")
    void saveNextTurn_MapsTurnUniqueConstraintToConflict() {
        given(gameSessionRepository.findByIdAndOwnerKey(42L, OWNER_KEY))
                .willThrow(new DataIntegrityViolationException("constraint uk_game_log_session_turn violated"));

        assertThatThrownBy(() -> persistenceService.saveNextTurn(
                OWNER_KEY, 42L, 1, "선택", "본문", "[]", null
        )).isInstanceOf(TurnConflictException.class);
    }
}
