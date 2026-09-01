package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.SkillCheckDecisionService;
import com.uctale.uctale.domain.game.SkillCheckOutcome;
import com.uctale.uctale.domain.game.SkillCheckResult;
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
class PostgresSkillCheckDecisionTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Long SESSION_ID = 5252L;
    private static final int EXPECTED_TURN = 3;
    private static final String FINGERPRINT = "a".repeat(64);

    @Autowired private GameMutationRequestService mutationService;
    @Autowired private SkillCheckDecisionService decisionService;
    @Autowired private GameMutationRequestRepository mutationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM game_turn_reservation");
        mutationRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 idempotency request는 provider 실패 후 재시도해도 저장된 Skill Check 판정을 재사용한다")
    void sameMutationRetry_ReusesDecision() {
        AtomicInteger factoryCalls = new AtomicInteger();
        var first = mutationService.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "skill-retry-key-01",
                SESSION_ID, EXPECTED_TURN, FINGERPRINT);
        SkillCheckResult firstResult = decisionService.getOrCreate(first.requestId(), first.reservationOwner(), () -> {
            factoryCalls.incrementAndGet();
            return result(7);
        });
        mutationService.markFailed(first.requestId(), first.reservationOwner());

        var retry = mutationService.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "skill-retry-key-01",
                SESSION_ID, EXPECTED_TURN, FINGERPRINT);
        SkillCheckResult retryResult = decisionService.getOrCreate(retry.requestId(), retry.reservationOwner(), () -> {
            factoryCalls.incrementAndGet();
            return result(19);
        });

        assertThat(retry.requestId()).isEqualTo(first.requestId());
        assertThat(retryResult).isEqualTo(firstResult);
        assertThat(retryResult.rawRoll()).isEqualTo(7);
        assertThat(factoryCalls).hasValue(1);
    }

    @Test
    @DisplayName("다른 mutation request가 만료 lease를 takeover하면 이전 request 판정을 재사용하지 않는다")
    void differentMutationTakeover_ReplacesDecision() {
        var first = mutationService.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "skill-owner-key-01",
                SESSION_ID, EXPECTED_TURN, FINGERPRINT);
        decisionService.getOrCreate(first.requestId(), first.reservationOwner(), () -> result(5));
        mutationService.markFailed(first.requestId(), first.reservationOwner());

        var second = mutationService.begin(OWNER_KEY, GameMutationRequestService.PROGRESS, "skill-owner-key-02",
                SESSION_ID, EXPECTED_TURN, "b".repeat(64));
        SkillCheckResult secondResult = decisionService.getOrCreate(second.requestId(), second.reservationOwner(), () -> result(15));

        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(secondResult.rawRoll()).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT skill_check_request_id FROM game_turn_reservation WHERE session_id = ? AND expected_turn = ?",
                Long.class, SESSION_ID, EXPECTED_TURN)).isEqualTo(second.requestId());
    }

    private SkillCheckResult result(int rawRoll) {
        SkillCheckOutcome outcome = rawRoll >= 10 ? SkillCheckOutcome.SUCCESS : SkillCheckOutcome.FAILURE;
        return new SkillCheckResult(StatType.WILL, rawRoll, 0, 0, 10, rawRoll, outcome, 1);
    }
}
