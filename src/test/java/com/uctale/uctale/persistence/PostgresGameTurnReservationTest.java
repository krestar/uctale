package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.MutationInProgressException;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresGameTurnReservationTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Long SESSION_ID = 4242L;
    private static final int EXPECTED_TURN = 7;

    @Autowired private GameMutationRequestService service;
    @Autowired private GameMutationRequestRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM game_turn_reservation");
        repository.deleteAll();
    }

    @Test
    @DisplayName("PostgreSQL에서는 같은 session/turn의 유효 lease를 하나만 소유하고 만료 뒤 회수할 수 있다")
    void activeLease_IsUniqueAndExpiredLeaseCanBeTakenOver() {
        var first = service.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "lease-key-001",
                SESSION_ID, EXPECTED_TURN, "a".repeat(64));

        assertThatThrownBy(() -> service.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "lease-key-002",
                SESSION_ID, EXPECTED_TURN, "b".repeat(64)))
                .isInstanceOf(MutationInProgressException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_turn_reservation WHERE session_id = ? AND expected_turn = ?",
                Integer.class, SESSION_ID, EXPECTED_TURN)).isEqualTo(1);

        jdbcTemplate.update("UPDATE game_turn_reservation SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");

        var second = service.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "lease-key-002",
                SESSION_ID, EXPECTED_TURN, "b".repeat(64));

        assertThat(second.reservationOwner()).isNotEqualTo(first.reservationOwner());
        assertThat(attemptCount()).isZero();
    }

    @Test
    @DisplayName("provider 호출 전 실패는 여러 번 발생해도 provider attempt를 소비하지 않는다")
    void preProviderFailures_DoNotConsumeProviderAttempts() {
        for (int index = 1; index <= 4; index++) {
            var reservation = service.begin(
                    OWNER_KEY,
                    GameMutationRequestService.PROGRESS,
                    "precall-key-00" + index,
                    SESSION_ID,
                    EXPECTED_TURN,
                    Integer.toString(index).repeat(64)
            );
            assertThat(attemptCount()).isZero();
            service.markFailed(reservation.requestId(), reservation.reservationOwner());
        }

        var valid = service.begin(
                OWNER_KEY,
                GameMutationRequestService.PROGRESS,
                "precall-valid-01",
                SESSION_ID,
                EXPECTED_TURN,
                "f".repeat(64)
        );
        service.markProviderAttemptStarted(valid.requestId(), valid.reservationOwner());

        assertThat(attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실제 provider 시작은 최대 세 번까지만 허용한다")
    void providerAttempts_AreBoundedToThree() {
        for (int index = 1; index <= 3; index++) {
            var reservation = service.begin(
                    OWNER_KEY,
                    GameMutationRequestService.PROGRESS,
                    "provider-key-00" + index,
                    SESSION_ID,
                    EXPECTED_TURN,
                    Integer.toString(index).repeat(64)
            );
            service.markProviderAttemptStarted(reservation.requestId(), reservation.reservationOwner());
            assertThat(attemptCount()).isEqualTo(index);
            service.markFailed(reservation.requestId(), reservation.reservationOwner());
        }

        assertThatThrownBy(() -> service.begin(
                OWNER_KEY,
                GameMutationRequestService.PROGRESS,
                "provider-key-004",
                SESSION_ID,
                EXPECTED_TURN,
                "4".repeat(64)
        )).isInstanceOf(MutationInProgressException.class);

        assertThat(attemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("회수된 stale owner는 provider attempt를 시작할 수 없다")
    void staleOwner_CannotStartProviderAttemptAfterTakeover() {
        var first = service.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "stale-key-001",
                SESSION_ID, EXPECTED_TURN, "a".repeat(64));
        service.markFailed(first.requestId(), first.reservationOwner());

        var second = service.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "stale-key-002",
                SESSION_ID, EXPECTED_TURN, "b".repeat(64));

        assertThatThrownBy(() -> service.markProviderAttemptStarted(first.requestId(), first.reservationOwner()))
                .isInstanceOf(MutationInProgressException.class);
        assertThat(attemptCount()).isZero();

        service.markProviderAttemptStarted(second.requestId(), second.reservationOwner());
        assertThat(attemptCount()).isEqualTo(1);
    }

    private int attemptCount() {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM game_turn_reservation WHERE session_id = ? AND expected_turn = ?",
                Integer.class, SESSION_ID, EXPECTED_TURN
        );
    }
}
