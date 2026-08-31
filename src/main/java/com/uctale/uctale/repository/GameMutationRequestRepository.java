package com.uctale.uctale.repository;

import com.uctale.uctale.domain.GameMutationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameMutationRequestRepository extends JpaRepository<GameMutationRequest, Long> {
    Optional<GameMutationRequest> findByOwnerKeyAndIdempotencyKey(String ownerKey, String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO game_mutation_request (
                owner_key, operation, idempotency_key, session_id, expected_turn,
                request_fingerprint, status, created_at, updated_at
            ) VALUES (
                :ownerKey, :operation, :idempotencyKey, :sessionId, :expectedTurn,
                :fingerprint, 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (owner_key, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("ownerKey") String ownerKey,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("sessionId") Long sessionId,
            @Param("expectedTurn") Integer expectedTurn,
            @Param("fingerprint") String fingerprint
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from GameMutationRequest request where request.ownerKey = :ownerKey and request.idempotencyKey = :idempotencyKey")
    Optional<GameMutationRequest> findForUpdate(
            @Param("ownerKey") String ownerKey,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Query(value = """
            SELECT lease_owner
            FROM game_turn_reservation
            WHERE session_id = :sessionId AND expected_turn = :expectedTurn
            FOR UPDATE
            """, nativeQuery = true)
    Optional<String> findReservationOwnerForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("expectedTurn") int expectedTurn
    );

    @Modifying
    @Query(value = """
            DELETE FROM game_turn_reservation
            WHERE session_id = :sessionId
              AND expected_turn = :expectedTurn
              AND request_id = :requestId
              AND lease_owner = :leaseOwner
            """, nativeQuery = true)
    int releaseReservation(
            @Param("sessionId") Long sessionId,
            @Param("expectedTurn") int expectedTurn,
            @Param("requestId") Long requestId,
            @Param("leaseOwner") String leaseOwner
    );
}
