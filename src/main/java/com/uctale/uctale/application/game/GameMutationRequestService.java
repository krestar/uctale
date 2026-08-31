package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GameMutationRequestService {

    public static final String INIT = "INIT";
    public static final String PROGRESS = "PROGRESS";
    private static final int MAX_RESERVATION_ATTEMPTS = 3;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final GameMutationRequestRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final long leaseSeconds;

    public GameMutationRequestService(
            GameMutationRequestRepository repository,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            @Value("${app.game.turn-reservation.lease-seconds:90}") long leaseSeconds
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.leaseSeconds = Math.max(10, leaseSeconds);
    }

    @Transactional(noRollbackFor = MutationInProgressException.class)
    public BeginResult begin(
            String ownerKey,
            String operation,
            String idempotencyKey,
            Long sessionId,
            Integer expectedTurn,
            String fingerprint
    ) {
        validateKey(idempotencyKey);

        int inserted = repository.insertIfAbsent(
                ownerKey, operation, idempotencyKey, sessionId, expectedTurn, fingerprint
        );
        GameMutationRequest request = repository.findForUpdate(ownerKey, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("mutation request를 찾을 수 없습니다."));

        if (!request.matches(operation, fingerprint)) {
            throw new IdempotencyConflictException("같은 Idempotency-Key가 다른 요청 payload에 재사용되었습니다.");
        }
        if (request.getStatus() == GameMutationRequest.Status.COMPLETED) {
            return BeginResult.replay(
                    request.getId(), request.getResultSessionId(), request.getResultTurn(), request.getResultTitle()
            );
        }
        if (inserted == 0 && request.getStatus() == GameMutationRequest.Status.PROCESSING) {
            throw new MutationInProgressException("동일한 idempotency 요청이 이미 처리 중입니다.", leaseSeconds);
        }
        if (request.getStatus() == GameMutationRequest.Status.FAILED) {
            request.restart();
            repository.save(request);
        }

        if (!PROGRESS.equals(operation)) {
            return BeginResult.process(request.getId(), null);
        }
        if (sessionId == null || expectedTurn == null) {
            throw new IllegalArgumentException("progress reservation에는 sessionId와 expectedTurn이 필요합니다.");
        }

        String leaseOwner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusSeconds(leaseSeconds);
        int acquired = jdbcTemplate.update("""
                INSERT INTO game_turn_reservation (
                    session_id, expected_turn, request_id, lease_owner, lease_expires_at, attempt_count,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (session_id, expected_turn) DO UPDATE SET
                    request_id = EXCLUDED.request_id,
                    lease_owner = EXCLUDED.lease_owner,
                    lease_expires_at = EXCLUDED.lease_expires_at,
                    attempt_count = game_turn_reservation.attempt_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE game_turn_reservation.lease_expires_at <= ?
                  AND game_turn_reservation.attempt_count < ?
                """, sessionId, expectedTurn, request.getId(), leaseOwner, expiresAt, now, MAX_RESERVATION_ATTEMPTS);

        if (acquired == 0) {
            request.fail();
            repository.save(request);
            long retryAfter = currentRetryAfterSeconds(sessionId, expectedTurn, now);
            throw new MutationInProgressException("같은 턴이 이미 처리 중이거나 재시도 한도에 도달했습니다.", retryAfter);
        }
        return BeginResult.process(request.getId(), leaseOwner);
    }

    @Transactional
    public void markFailed(Long requestId) {
        repository.findById(requestId).ifPresent(request -> {
            request.fail();
            repository.save(request);
            jdbcTemplate.update("""
                    UPDATE game_turn_reservation
                    SET lease_expires_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE request_id = ?
                    """, requestId);
        });
    }

    private long currentRetryAfterSeconds(Long sessionId, int expectedTurn, LocalDateTime now) {
        return jdbcTemplate.query(
                "SELECT lease_expires_at FROM game_turn_reservation WHERE session_id = ? AND expected_turn = ?",
                rs -> rs.next()
                        ? Math.max(1, ChronoUnit.SECONDS.between(now, rs.getTimestamp(1).toLocalDateTime()))
                        : 1,
                sessionId, expectedTurn
        );
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key는 8~128자의 영문, 숫자, '.', '_', ':', '-'만 사용할 수 있습니다."
            );
        }
    }

    public record BeginResult(
            Long requestId,
            boolean replay,
            Long resultSessionId,
            Integer resultTurn,
            String resultTitle,
            String reservationOwner
    ) {
        public BeginResult(Long requestId, boolean replay, Long resultSessionId, Integer resultTurn, String resultTitle) {
            this(requestId, replay, resultSessionId, resultTurn, resultTitle, null);
        }

        static BeginResult process(Long requestId, String reservationOwner) {
            return new BeginResult(requestId, false, null, null, null, reservationOwner);
        }

        static BeginResult replay(Long requestId, Long sessionId, Integer turn, String title) {
            return new BeginResult(requestId, true, sessionId, turn, title, null);
        }
    }
}
