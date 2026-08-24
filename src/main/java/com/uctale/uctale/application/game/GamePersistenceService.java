package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GamePersistenceService {

    private final GameSessionRepository gameSessionRepository;
    private final GameLogRepository gameLogRepository;

    public GamePersistenceService(GameSessionRepository gameSessionRepository, GameLogRepository gameLogRepository) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameLogRepository = gameLogRepository;
    }

    @Transactional
    public GameSession saveOpening(
            String worldSetting,
            String characterSetting,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        GameSession session = gameSessionRepository.save(new GameSession(worldSetting, characterSetting));
        gameLogRepository.save(new GameLog(session, 1, storyText, choicesJson, imageUrl));
        return session;
    }

    @Transactional(readOnly = true)
    public LoadedTurn loadLatestTurn(Long sessionId, int expectedTurn) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다."));

        if (session.getCurrentTurn() != expectedTurn) {
            throw new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다.");
        }

        GameLog lastLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

        if (lastLog.getTurnNumber() != expectedTurn) {
            throw new IllegalStateException("세션 턴과 저장된 로그가 일치하지 않습니다.");
        }

        return new LoadedTurn(
                session.getId(),
                session.getCurrentTurn(),
                session.getWorldSetting(),
                session.getCharacterSetting(),
                lastLog.getStoryText(),
                lastLog.getChoicesJson(),
                lastLog.getImageUrl()
        );
    }

    @Transactional
    public int saveNextTurn(
            Long sessionId,
            int expectedTurn,
            String userChoice,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        try {
            GameSession session = gameSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다."));

            if (session.getCurrentTurn() != expectedTurn) {
                throw new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다.");
            }

            GameLog previousLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                    .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

            if (previousLog.getTurnNumber() != expectedTurn) {
                throw new IllegalStateException("세션 턴과 저장된 로그가 일치하지 않습니다.");
            }

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

            gameLogRepository.flush();
            gameSessionRepository.flush();
            return session.getCurrentTurn();
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw new TurnConflictException("동시에 처리된 턴 요청과 충돌했습니다.", exception);
        }
    }

    public record LoadedTurn(
            Long sessionId,
            int turnNumber,
            String worldSetting,
            String characterSetting,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {}
}
