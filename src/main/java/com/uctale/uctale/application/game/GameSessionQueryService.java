package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.dto.SessionResumeResponse;
import com.uctale.uctale.dto.SessionSummaryResponse;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class GameSessionQueryService {

    private static final int MAX_PROVIDER_ATTEMPTS = 3;
    private static final int FALLBACK_TITLE_LENGTH = 80;

    private final GameSessionRepository gameSessionRepository;
    private final GameLogRepository gameLogRepository;
    private final GameStateSnapshotRepository gameStateSnapshotRepository;
    private final GameMutationRequestRepository gameMutationRequestRepository;
    private final GameStateCodec gameStateCodec;
    private final GameStateRecovery gameStateRecovery;
    private final ChoiceCodec choiceCodec;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public GameSessionQueryService(
            GameSessionRepository gameSessionRepository,
            GameLogRepository gameLogRepository,
            GameStateSnapshotRepository gameStateSnapshotRepository,
            GameMutationRequestRepository gameMutationRequestRepository,
            GameStateCodec gameStateCodec,
            GameStateRecovery gameStateRecovery,
            ChoiceCodec choiceCodec,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameLogRepository = gameLogRepository;
        this.gameStateSnapshotRepository = gameStateSnapshotRepository;
        this.gameMutationRequestRepository = gameMutationRequestRepository;
        this.gameStateCodec = gameStateCodec;
        this.gameStateRecovery = gameStateRecovery;
        this.choiceCodec = choiceCodec;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<SessionSummaryResponse> listSessions(String ownerKey) {
        return gameSessionRepository.findByOwnerKeyOrderByUpdatedAtDesc(ownerKey).stream()
                .map(session -> inspect(ownerKey, session))
                .map(SessionView::summary)
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SessionResumeResponse resumeSession(String ownerKey, Long sessionId) {
        GameSession session = gameSessionRepository.findByIdAndOwnerKey(sessionId, ownerKey)
                .orElseThrow(() -> new GameSessionNotFoundException("존재하지 않는 세션입니다."));
        SessionView view = inspect(ownerKey, session);
        if (view.completedTurn() == null) {
            return new SessionResumeResponse(
                    session.getId(), view.title(), session.getCurrentTurn(), session.getUpdatedAt(),
                    view.status().name(), view.statusMessage(), false, false, null, null
            );
        }

        CompletedTurn completedTurn = view.completedTurn();
        try {
            GameResponse game = GameResponseProjector.projectReplay(
                    session.getId(), session.getCurrentTurn(), view.title(), completedTurn.log().getStoryText(),
                    completedTurn.choices(), completedTurn.log().getImageUrl(), completedTurn.gameState(),
                    GamePersistenceService.SkillCheckAudit.from(completedTurn.log())
            );
            boolean canProgress = !session.isGameOver()
                    && (view.status() == SessionStatus.READY
                    || (view.status() == SessionStatus.FAILED && view.retryable()));
            return new SessionResumeResponse(
                    session.getId(), view.title(), session.getCurrentTurn(), session.getUpdatedAt(),
                    view.status().name(), view.statusMessage(), view.retryable(), canProgress,
                    completedTurn.gameState().turnNumber(), game
            );
        } catch (RuntimeException exception) {
            log.warn("세션 resume projection을 안전하게 구성할 수 없습니다. sessionId={}", session.getId(), exception);
            return unrecoverableResume(session, view.title());
        }
    }

    private SessionView inspect(String ownerKey, GameSession session) {
        GameLog latestLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session).orElse(null);
        String title = resolveTitle(ownerKey, session);
        String thumbnailUrl = latestLog == null ? null : latestLog.getImageUrl();

        CompletedTurn completedTurn;
        try {
            completedTurn = loadCompletedTurn(session, latestLog);
        } catch (RuntimeException exception) {
            log.warn("세션 canonical state를 안전하게 복구할 수 없습니다. sessionId={}", session.getId(), exception);
            return new SessionView(
                    session, title, thumbnailUrl, SessionStatus.UNRECOVERABLE,
                    "저장된 canonical 상태를 안전하게 복구할 수 없습니다. 마지막 완료 턴 데이터는 변경하지 않았습니다.",
                    false, null
            );
        }

        ProcessingState processing = processingState(ownerKey, session);
        return new SessionView(
                session, title, thumbnailUrl, processing.status(), processing.message(),
                processing.retryable(), completedTurn
        );
    }

    private CompletedTurn loadCompletedTurn(GameSession session, GameLog latestLog) {
        if (latestLog == null) {
            throw new IllegalStateException("완료된 게임 로그가 없습니다.");
        }
        if (latestLog.getTurnNumber() != session.getCurrentTurn()
                || latestLog.getStateVersion() != session.getCurrentTurn()) {
            throw new IllegalStateException("세션과 마지막 완료 턴의 version이 일치하지 않습니다.");
        }

        GameState state = gameStateSnapshotRepository.findById(session.getId())
                .map(snapshot -> gameStateCodec.deserialize(snapshot.getStateJson()))
                .orElseGet(() -> gameStateRecovery.recover(
                        session, gameLogRepository.findByGameSessionOrderByTurnNumberAsc(session)
                ));
        if (state.turnNumber() != session.getCurrentTurn()) {
            throw new IllegalStateException("canonical state와 마지막 완료 턴이 일치하지 않습니다.");
        }

        List<GameChoice> choices = choiceCodec.deserialize(latestLog.getChoicesJson());
        return new CompletedTurn(latestLog, state, choices);
    }

    private ProcessingState processingState(String ownerKey, GameSession session) {
        ReservationState reservation = findReservation(session.getId(), session.getCurrentTurn());
        LocalDateTime now = LocalDateTime.now(clock);
        if (reservation != null && reservation.leaseExpiresAt().isAfter(now)) {
            return new ProcessingState(
                    SessionStatus.PROCESSING,
                    "새 턴을 처리 중입니다. 완료되기 전까지 마지막 완료 턴을 안전하게 표시합니다.",
                    false
            );
        }

        GameMutationRequest latestRequest = gameMutationRequestRepository
                .findTopByOwnerKeyAndSessionIdAndExpectedTurnAndOperationOrderByUpdatedAtDesc(
                        ownerKey, session.getId(), session.getCurrentTurn(), GameMutationRequestService.PROGRESS
                )
                .orElse(null);
        boolean exhausted = reservation != null && reservation.providerAttemptCount() >= MAX_PROVIDER_ATTEMPTS;
        if (exhausted) {
            return new ProcessingState(
                    SessionStatus.FAILED,
                    "진행 요청의 provider 재시도 한도에 도달했습니다. 마지막 완료 턴은 보존되어 있습니다.",
                    false
            );
        }
        if (latestRequest != null && (latestRequest.getStatus() == GameMutationRequest.Status.FAILED
                || latestRequest.getStatus() == GameMutationRequest.Status.PROCESSING)) {
            return new ProcessingState(
                    SessionStatus.FAILED,
                    "이전 진행 요청이 완료되지 않았습니다. 마지막 완료 턴에서 다시 선택할 수 있습니다.",
                    true
            );
        }
        return new ProcessingState(
                SessionStatus.READY,
                "마지막 완료 턴에서 계속할 수 있습니다.",
                false
        );
    }

    private ReservationState findReservation(Long sessionId, int expectedTurn) {
        return jdbcTemplate.query(
                """
                        SELECT lease_expires_at, provider_attempt_count
                        FROM game_turn_reservation
                        WHERE session_id = ? AND expected_turn = ?
                        """,
                rs -> rs.next()
                        ? new ReservationState(
                        rs.getTimestamp("lease_expires_at").toLocalDateTime(),
                        rs.getInt("provider_attempt_count")
                )
                        : null,
                sessionId, expectedTurn
        );
    }

    private String resolveTitle(String ownerKey, GameSession session) {
        return gameMutationRequestRepository
                .findTopByOwnerKeyAndResultSessionIdAndResultTurnAndStatusOrderByUpdatedAtDesc(
                        ownerKey, session.getId(), session.getCurrentTurn(), GameMutationRequest.Status.COMPLETED
                )
                .map(GameMutationRequest::getResultTitle)
                .filter(value -> value != null && !value.isBlank())
                .orElseGet(() -> fallbackTitle(session.getWorldSetting()));
    }

    private String fallbackTitle(String worldSetting) {
        if (worldSetting == null || worldSetting.isBlank()) {
            return "저장된 이야기";
        }
        String firstLine = worldSetting.strip().lines().findFirst().orElse("저장된 이야기").strip();
        if (firstLine.length() <= FALLBACK_TITLE_LENGTH) {
            return firstLine;
        }
        return firstLine.substring(0, FALLBACK_TITLE_LENGTH - 1) + "…";
    }

    private SessionResumeResponse unrecoverableResume(GameSession session, String title) {
        return new SessionResumeResponse(
                session.getId(), title, session.getCurrentTurn(), session.getUpdatedAt(),
                SessionStatus.UNRECOVERABLE.name(),
                "저장된 canonical 상태를 안전하게 복구할 수 없습니다. 마지막 완료 턴 데이터는 변경하지 않았습니다.",
                false, false, null, null
        );
    }

    private enum SessionStatus {
        READY,
        PROCESSING,
        FAILED,
        UNRECOVERABLE
    }

    private record ReservationState(LocalDateTime leaseExpiresAt, int providerAttemptCount) {}

    private record ProcessingState(SessionStatus status, String message, boolean retryable) {}

    private record CompletedTurn(GameLog log, GameState gameState, List<GameChoice> choices) {}

    private record SessionView(
            GameSession session,
            String title,
            String thumbnailUrl,
            SessionStatus status,
            String statusMessage,
            boolean retryable,
            CompletedTurn completedTurn
    ) {
        SessionSummaryResponse summary() {
            return new SessionSummaryResponse(
                    session.getId(), title, session.getCurrentTurn(), session.getUpdatedAt(),
                    status.name(), statusMessage, retryable, completedTurn != null, thumbnailUrl
            );
        }
    }
}
