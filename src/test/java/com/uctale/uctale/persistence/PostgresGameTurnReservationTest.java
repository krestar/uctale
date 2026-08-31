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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM game_turn_reservation WHERE session_id = ? AND expected_turn = ?",
                Integer.class, SESSION_ID, EXPECTED_TURN)).isEqualTo(2);
    }
}
