package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameLogRepository extends JpaRepository<GameLog, Long> {
    Optional<GameLog> findTopByGameSessionOrderByTurnNumberDesc(GameSession gameSession);

    Optional<GameLog> findByGameSessionAndTurnNumber(GameSession gameSession, int turnNumber);

    List<GameLog> findByGameSessionOrderByTurnNumberAsc(GameSession gameSession);
}
