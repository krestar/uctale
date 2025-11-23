package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameLogRepository extends JpaRepository<GameLog, Long> {
}