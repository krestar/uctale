package com.uctale.uctale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_mutation_request", uniqueConstraints = @UniqueConstraint(
        name = "uk_game_mutation_owner_operation_key",
        columnNames = {"owner_key", "operation", "idempotency_key"}
))
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GameMutationRequest {

    public enum Status { PROCESSING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_key", nullable = false, length = 64)
    private String ownerKey;

    @Column(nullable = false, length = 16)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "expected_turn")
    private Integer expectedTurn;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "result_session_id")
    private Long resultSessionId;

    @Column(name = "result_turn")
    private Integer resultTurn;

    @Column(name = "result_title", length = 200)
    private String resultTitle;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public GameMutationRequest(
            String ownerKey,
            String operation,
            String idempotencyKey,
            Long sessionId,
            Integer expectedTurn,
            String requestFingerprint
    ) {
        this.ownerKey = ownerKey;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.sessionId = sessionId;
        this.expectedTurn = expectedTurn;
        this.requestFingerprint = requestFingerprint;
        this.status = Status.PROCESSING;
    }

    public boolean hasFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public void restart() {
        if (status == Status.COMPLETED) {
            throw new IllegalStateException("완료된 mutation request는 재시작할 수 없습니다.");
        }
        status = Status.PROCESSING;
    }

    public void complete(Long sessionId, int turn, String title) {
        this.status = Status.COMPLETED;
        this.resultSessionId = sessionId;
        this.resultTurn = turn;
        this.resultTitle = title;
    }

    public void fail() {
        if (status == Status.PROCESSING) {
            status = Status.FAILED;
        }
    }
}
