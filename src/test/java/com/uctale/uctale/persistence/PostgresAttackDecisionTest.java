package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.AttackDecisionService;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.domain.game.AttackOutcome;
import com.uctale.uctale.domain.game.AttackResult;
import com.uctale.uctale.domain.game.StatType;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresAttackDecisionTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Long SESSION_ID = 6262L;
    private static final int EXPECTED_TURN = 3;
    private static final String FINGERPRINT = "c".repeat(64);

    @Autowired private GameMutationRequestService mutationService;
    @Autowired private AttackDecisionService decisionService;
    @Autowired private GameMutationRequestRepository mutationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM game_turn_reservation");
        mutationRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 idempotency request 재시도는 최초 Attack roll과 damage를 그대로 재사용한다")
    void sameMutationRetry_ReusesAttackDecision() {
        AtomicInteger calls = new AtomicInteger();
        var first = mutationService.begin(
                OWNER_KEY, GameMutationRequestService.PROGRESS, "attack-retry-key-01",
                SESSION_ID, EXPECTED_TURN, FINGERPRINT
        );
        AttackResult firstResult = decisionService.getOrCreate(first.requestId(), first.reservationOwner(), () -> {
            calls.incrementAndGet();
            return result(12, 4);
        });
        mutationService.markFailed(first.requestId(), first.reservationOwner());

        var retry = mutationService.begin(
                OWNER_KEY, GameMutationRequestService.PROGRESS, "attack-retry-key-01",
                SESSION_ID, EXPECTED_TURN, FINGERPRINT
        );
        AttackResult retryResult = decisionService.getOrCreate(retry.requestId(), retry.reservationOwner(), () -> {
            calls.incrementAndGet();
            return result(20, 6);
        });

        assertThat(retry.requestId()).isEqualTo(first.requestId());
        assertThat(retryResult).isEqualTo(firstResult);
        assertThat(retryResult.attackRoll()).isEqualTo(12);
        assertThat(retryResult.damageRoll()).isEqualTo(4);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("다른 mutation request가 만료 reservation을 takeover하면 이전 Attack 판정을 재사용하지 않는다")
    void differentMutationTakeover_ReplacesAttackDecision() {
        var first = mutationService.begin(
                OWNER_KEY, GameMutationRequestService.PROGRESS, "attack-owner-key-01",
                SESSION_ID, EXPECTED_TURN, FINGERPRINT
        );
        decisionService.getOrCreate(first.requestId(), first.reservationOwner(), () -> result(11, 2));
        mutationService.markFailed(first.requestId(), first.reservationOwner());

        var second = mutationService.begin(
                OWNER_KEY, GameMutationRequestService.PROGRESS, "attack-owner-key-02",
                SESSION_ID, EXPECTED_TURN, "d".repeat(64)
        );
        AttackResult secondResult = decisionService.getOrCreate(
                second.requestId(), second.reservationOwner(), () -> result(18, 5)
        );

        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(secondResult.attackRoll()).isEqualTo(18);
        assertThat(secondResult.damageRoll()).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attack_result_request_id FROM game_turn_reservation WHERE session_id = ? AND expected_turn = ?",
                Long.class, SESSION_ID, EXPECTED_TURN
        )).isEqualTo(second.requestId());
    }

    private AttackResult result(int attackRoll, int damageRoll) {
        return new AttackResult(
                "enc-1", "wolf", StatType.MIGHT,
                attackRoll, 0, 0, 10, attackRoll, AttackOutcome.HIT,
                damageRoll, 0, 0, damageRoll, 0, damageRoll, damageRoll,
                10, 10 - damageRoll, false, 1
        );
    }
}
