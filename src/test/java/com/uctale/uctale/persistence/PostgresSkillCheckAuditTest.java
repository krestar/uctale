package com.uctale.uctale.persistence;

import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.game.SkillCheckOutcome;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StatType;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresSkillCheckAuditTest extends PostgresIntegrationTestSupport {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired private GameSessionRepository sessionRepository;
    @Autowired private GameLogRepository logRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM game_turn_reservation");
        jdbcTemplate.update("DELETE FROM game_mutation_request");
        logRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    @DisplayName("GameLog에서 raw roll부터 outcome까지 Skill Check 감사 정보를 조회할 수 있다")
    void committedLog_PersistsCompleteSkillCheckAudit() {
        GameSession session = sessionRepository.saveAndFlush(new GameSession(OWNER_KEY, "세계", "캐릭터"));
        SkillCheckResult result = new SkillCheckResult(
                StatType.WILL, 13, 0, -1, 12, 12, SkillCheckOutcome.SUCCESS, 1);
        logRepository.saveAndFlush(GameLog.committedTurn(
                session, 2, 1, "문을 연다", 1, 2,
                "game-result:1:2:1", "story:test", result,
                "문이 열렸다.", "[]", null));

        GameLog saved = logRepository.findByGameSessionAndTurnNumber(session, 2).orElseThrow();
        assertThat(saved.getSkillCheckStatType()).isEqualTo("WILL");
        assertThat(saved.getSkillCheckRawRoll()).isEqualTo(13);
        assertThat(saved.getSkillCheckStatModifier()).isZero();
        assertThat(saved.getSkillCheckSituationalModifier()).isEqualTo(-1);
        assertThat(saved.getSkillCheckDc()).isEqualTo(12);
        assertThat(saved.getSkillCheckTotal()).isEqualTo(12);
        assertThat(saved.getSkillCheckOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getSkillCheckRulesetVersion()).isEqualTo(1);
    }
}
