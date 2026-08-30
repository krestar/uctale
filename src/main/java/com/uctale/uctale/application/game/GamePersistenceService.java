package com.uctale.uctale.application.game;

import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.GameStateSnapshot;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import com.uctale.uctale.repository.ImageAssetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GamePersistenceService {

    private static final String GAME_LOG_TURN_UNIQUE_CONSTRAINT = "uk_game_log_session_turn";
    private static final String IMAGE_ASSET_TURN_UNIQUE_CONSTRAINT = "uk_image_asset_session_turn";

    private final GameSessionRepository gameSessionRepository;
    private final GameLogRepository gameLogRepository;
    private final GameStateSnapshotRepository gameStateSnapshotRepository;
    private final ImageAssetRepository imageAssetRepository;
    private final GameStateCodec gameStateCodec;

    public GamePersistenceService(
            GameSessionRepository gameSessionRepository,
            GameLogRepository gameLogRepository,
            GameStateSnapshotRepository gameStateSnapshotRepository,
            ImageAssetRepository imageAssetRepository,
            GameStateCodec gameStateCodec
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameLogRepository = gameLogRepository;
        this.gameStateSnapshotRepository = gameStateSnapshotRepository;
        this.imageAssetRepository = imageAssetRepository;
        this.gameStateCodec = gameStateCodec;
    }

    @Transactional
    public GameSession saveOpening(
            String ownerKey,
            String worldSetting,
            String characterSetting,
            String storyText,
            String choicesJson,
            ImageAssetService.AssetReference imageAsset
    ) {
        try {
            GameSession session = gameSessionRepository.save(new GameSession(ownerKey, worldSetting, characterSetting));
            String imageUrl = persistImageAsset(session, 1, imageAsset);
            GameState initialState = GameState.initial(worldSetting, characterSetting, storyText);
            gameStateSnapshotRepository.save(new GameStateSnapshot(session, gameStateCodec.serialize(initialState)));
            gameLogRepository.save(GameLog.opening(session, storyText, choicesJson, imageUrl));
            flushAll();
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
        if (gameState.turnNumber() != expectedTurn || lastLog.getStateVersion() != expectedTurn) {
            throw new IllegalStateException("세션 턴과 canonical state version이 일치하지 않습니다.");
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
    public int saveNextTurn(String ownerKey, Long sessionId, GameTurnCommit commit) {
        try {
            GameSession session = findOwnedSession(ownerKey, sessionId);
            validateCommitAgainstSession(session, commit);

            GameLog previousLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                    .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

            if (previousLog.getTurnNumber() != commit.expectedTurn()
                    || previousLog.getStateVersion() != commit.previousStateVersion()) {
                throw new IllegalStateException("세션 턴과 저장된 로그의 state version이 일치하지 않습니다.");
            }

            session.advanceTurn();

            String imageUrl = commit.imageAsset() == null
                    ? previousLog.getImageUrl()
                    : persistImageAsset(session, commit.nextStateVersion(), commit.imageAsset());

            gameSessionRepository.save(session);
            gameLogRepository.save(GameLog.committedTurn(
                    session,
                    commit.nextStateVersion(),
                    commit.inputChoiceId(),
                    commit.inputChoiceText(),
                    commit.previousStateVersion(),
                    commit.nextStateVersion(),
                    commit.storyText(),
                    commit.choicesJson(),
                    imageUrl
            ));

            GameStateSnapshot snapshot = gameStateSnapshotRepository.findById(sessionId)
                    .orElseGet(() -> new GameStateSnapshot(session, gameStateCodec.serialize(commit.previousState())));
            snapshot.updateStateJson(gameStateCodec.serialize(commit.nextState()));
            gameStateSnapshotRepository.save(snapshot);

            flushAll();
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

    private void validateCommitAgainstSession(GameSession session, GameTurnCommit commit) {
        if (session.getCurrentTurn() != commit.expectedTurn()) {
            throw new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다.");
        }
        if (commit.previousStateVersion() != commit.expectedTurn()) {
            throw new IllegalArgumentException("commit의 이전 state version이 expectedTurn과 일치하지 않습니다.");
        }
        if (commit.nextStateVersion() != commit.expectedTurn() + 1) {
            throw new IllegalArgumentException("commit의 다음 state version이 올바르지 않습니다.");
        }
    }

    private String persistImageAsset(
            GameSession session,
            int turnNumber,
            ImageAssetService.AssetReference assetReference
    ) {
        if (assetReference == null) {
            return null;
        }
        ImageAsset asset = new ImageAsset(
                assetReference.id(),
                session,
                turnNumber,
                assetReference.prompt(),
                assetReference.aspectRatio(),
                assetReference.model(),
                assetReference.width(),
                assetReference.height(),
                assetReference.seed(),
                assetReference.safe(),
                assetReference.styleVersion()
        );
        imageAssetRepository.save(asset);
        return asset.publicUrl();
    }

    private GameSession findOwnedSession(String ownerKey, Long sessionId) {
        return gameSessionRepository.findByIdAndOwnerKey(sessionId, ownerKey)
                .orElseThrow(() -> new GameSessionNotFoundException("존재하지 않는 세션입니다."));
    }

    private boolean isTurnUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains(GAME_LOG_TURN_UNIQUE_CONSTRAINT)
                || normalized.contains(IMAGE_ASSET_TURN_UNIQUE_CONSTRAINT);
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

        GameLog opening = logs.getFirst();
        if (opening.getStateVersion() != 1) {
            throw new IllegalStateException("Opening GameLog의 state version이 올바르지 않습니다.");
        }

        GameState state = GameState.initial(
                session.getWorldSetting(),
                session.getCharacterSetting(),
                opening.getStoryText()
        );
        for (int i = 1; i < logs.size(); i++) {
            GameLog log = logs.get(i);
            if (log.getPreviousStateVersion() != state.turnNumber()
                    || log.getStateVersion() != state.turnNumber() + 1
                    || log.getInputChoiceText() == null
                    || log.getInputChoiceText().isBlank()) {
                throw new IllegalStateException("GameLog state transition을 복구할 수 없습니다.");
            }
            state = state.advance(log.getInputChoiceText(), log.getStoryText());
        }
        return state;
    }

    private void flushAll() {
        imageAssetRepository.flush();
        gameLogRepository.flush();
        gameStateSnapshotRepository.flush();
        gameSessionRepository.flush();
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
