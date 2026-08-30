package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByIdAndOwnerKey(Long id, String ownerKey);
}
