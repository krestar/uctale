package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameMutationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameMutationRequestRepository extends JpaRepository<GameMutationRequest, Long> {
    Optional<GameMutationRequest> findByOwnerKeyAndIdempotencyKey(String ownerKey, String idempotencyKey);
}
