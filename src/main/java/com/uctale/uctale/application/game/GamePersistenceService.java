package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
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
    public LoadedTurn loadLatestTurn(Long sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다."));
        GameLog lastLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));
        return new LoadedTurn(session, lastLog);
    }

    @Transactional
    public void saveNextTurn(
            GameLog previousLog,
            String userChoice,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        previousLog.updateUserChoice(userChoice);
        gameLogRepository.save(previousLog);
        gameLogRepository.save(new GameLog(
                previousLog.getGameSession(),
                previousLog.getTurnNumber() + 1,
                storyText,
                choicesJson,
                imageUrl
        ));
    }

    public record LoadedTurn(GameSession session, GameLog log) {}
}
