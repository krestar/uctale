package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameStateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameStateSnapshotRepository extends JpaRepository<GameStateSnapshot, Long> {
}
