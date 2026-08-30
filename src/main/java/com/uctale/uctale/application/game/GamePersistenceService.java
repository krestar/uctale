package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.GameStateSnapshot;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GamePersistenceService {

    private static final String TURN_UNIQUE_CONSTRAINT = "uk_game_log_session_turn";

    private final GameSessionRepository gameSessionRepository;
    private final GameLogRepository gameLogRepository;
    private final GameStateSnapshotRepository gameStateSnapshotRepository;
    private final GameStateCodec gameStateCodec;

    public GamePersistenceService(
            GameSessionRepository gameSessionRepository,
            GameLogRepository gameLogRepository,
            GameStateSnapshotRepository gameStateSnapshotRepository,
            GameStateCodec gameStateCodec
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameLogRepository = gameLogRepository;
        this.gameStateSnapshotRepository = gameStateSnapshotRepository;
        this.gameStateCodec = gameStateCodec;
    }

    @Transactional
    public GameSession saveOpening(
            String ownerKey,
            String worldSetting,
            String characterSetting,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        try {
            GameSession session = gameSessionRepository.save(new GameSession(ownerKey, worldSetting, characterSetting));
            GameState initialState = GameState.initial(worldSetting, characterSetting, storyText);
            gameStateSnapshotRepository.save(new GameStateSnapshot(session, gameStateCodec.serialize(initialState)));
            gameLogRepository.save(new GameLog(session, 1, storyText, choicesJson, imageUrl));
            gameLogRepository.flush();
            gameStateSnapshotRepository.flush();
            gameSessionRepository.flush();
            return session;
        } catch (DataIntegrityViolationException exception) {
            throw new PersistenceOperationException("게임 시작 상태를 저장할 수 없습니다.", exception);
        }
    }

    @Transactional(readOnly = true)
    public LoadedTurn loadLatestTurn(String ownerKey, Long sessionId, int expectedTurn) {
        GameSession session = findOwnedSession(ownerKey, sessionId);

        if (session.getCurrentTurn() != expectedTurn) {
            throw new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다.");
        }

        GameLog lastLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

        if (lastLog.getTurnNumber() != expectedTurn) {
            throw new IllegalStateException("세션 턴과 저장된 로그가 일치하지 않습니다.");
        }

        GameState gameState = loadOrRecoverState(session);
        if (gameState.turnNumber() != expectedTurn) {
            throw new IllegalStateException("세션 턴과 GameState가 일치하지 않습니다.");
        }

        return new LoadedTurn(
                session.getId(),
                session.getCurrentTurn(),
                session.getWorldSetting(),
                session.getCharacterSetting(),
                lastLog.getStoryText(),
                lastLog.getChoicesJson(),
                lastLog.getImageUrl(),
                gameState
        );
    }

    @Transactional
    public int saveNextTurn(
            String ownerKey,
            Long sessionId,
            int expectedTurn,
            String userChoice,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        try {
            GameSession session = findOwnedSession(ownerKey, sessionId);

            if (session.getCurrentTurn() != expectedTurn) {
                throw new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다.");
            }

            GameLog previousLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                    .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

            if (previousLog.getTurnNumber() != expectedTurn) {
                throw new IllegalStateException("세션 턴과 저장된 로그가 일치하지 않습니다.");
            }

            GameState currentState = loadOrRecoverState(session);
            if (currentState.turnNumber() != expectedTurn) {
                throw new IllegalStateException("세션 턴과 GameState가 일치하지 않습니다.");
            }
            GameState nextState = currentState.advance(userChoice, storyText);

            previousLog.updateUserChoice(userChoice);
            session.advanceTurn();

            gameLogRepository.save(previousLog);
            gameSessionRepository.save(session);
            gameLogRepository.save(new GameLog(
                    session,
                    expectedTurn + 1,
                    storyText,
                    choicesJson,
                    imageUrl
            ));

            GameStateSnapshot snapshot = gameStateSnapshotRepository.findById(sessionId)
                    .orElseGet(() -> new GameStateSnapshot(session, gameStateCodec.serialize(currentState)));
            snapshot.updateStateJson(gameStateCodec.serialize(nextState));
            gameStateSnapshotRepository.save(snapshot);

            gameLogRepository.flush();
            gameStateSnapshotRepository.flush();
            gameSessionRepository.flush();
            return session.getCurrentTurn();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new TurnConflictException("동시에 처리된 턴 요청과 충돌했습니다.", exception);
        } catch (DataIntegrityViolationException exception) {
            if (isTurnUniqueConstraintViolation(exception)) {
                throw new TurnConflictException("동시에 처리된 턴 요청과 충돌했습니다.", exception);
            }
            throw new PersistenceOperationException("게임 진행 상태를 저장할 수 없습니다.", exception);
        }
    }

    private GameSession findOwnedSession(String ownerKey, Long sessionId) {
        return gameSessionRepository.findByIdAndOwnerKey(sessionId, ownerKey)
                .orElseThrow(() -> new GameSessionNotFoundException("존재하지 않는 세션입니다."));
    }

    private boolean isTurnUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        return message != null && message.toLowerCase().contains(TURN_UNIQUE_CONSTRAINT);
    }

    private GameState loadOrRecoverState(GameSession session) {
        return gameStateSnapshotRepository.findById(session.getId())
                .map(snapshot -> gameStateCodec.deserialize(snapshot.getStateJson()))
                .orElseGet(() -> recoverState(session));
    }

    private GameState recoverState(GameSession session) {
        List<GameLog> logs = gameLogRepository.findByGameSessionOrderByTurnNumberAsc(session);
        if (logs.isEmpty()) {
            throw new IllegalStateException("GameState를 복구할 게임 로그가 없습니다.");
        }

        GameState state = GameState.initial(
                session.getWorldSetting(),
                session.getCharacterSetting(),
                logs.get(0).getStoryText()
        );
        for (int i = 1; i < logs.size(); i++) {
            String action = logs.get(i - 1).getUserChoice();
            state = state.advance(action, logs.get(i).getStoryText());
        }
        return state;
    }

    public record LoadedTurn(
            Long sessionId,
            int turnNumber,
            String worldSetting,
            String characterSetting,
            String storyText,
            String choicesJson,
            String imageUrl,
            GameState gameState
    ) {}
}
