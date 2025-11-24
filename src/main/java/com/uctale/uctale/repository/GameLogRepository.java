package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameLogRepository extends JpaRepository<GameLog, Long> {
    Optional<GameLog> findTopByGameSessionOrderByTurnNumberDesc(GameSession gameSession);
}