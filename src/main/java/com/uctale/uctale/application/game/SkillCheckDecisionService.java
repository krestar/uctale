package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.SkillCheckOutcome;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StatType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class SkillCheckDecisionService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SkillCheckDecisionService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public SkillCheckResult getOrCreate(
            Long requestId,
            String reservationOwner,
            Supplier<SkillCheckResult> decisionFactory
    ) {
        if (requestId == null || reservationOwner == null || reservationOwner.isBlank()) {
            throw new IllegalArgumentException("Skill Check 판정에는 유효한 reservation 소유권이 필요합니다.");
        }
        Objects.requireNonNull(decisionFactory, "decisionFactory는 필수입니다.");
        LocalDateTime now = LocalDateTime.now(clock);
        List<StoredDecision> decisions = jdbcTemplate.query(
                """
                        SELECT skill_check_stat_type, skill_check_raw_roll, skill_check_stat_modifier,
                               skill_check_situational_modifier, skill_check_dc, skill_check_total,
                               skill_check_outcome, skill_check_ruleset_version
                        FROM game_turn_reservation
                        WHERE request_id = ? AND lease_owner = ? AND lease_expires_at > ?
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new StoredDecision(
                        rs.getString("skill_check_stat_type"),
                        nullableInt(rs, "skill_check_raw_roll"),
                        nullableInt(rs, "skill_check_stat_modifier"),
                        nullableInt(rs, "skill_check_situational_modifier"),
                        nullableInt(rs, "skill_check_dc"),
                        nullableInt(rs, "skill_check_total"),
                        rs.getString("skill_check_outcome"),
                        nullableInt(rs, "skill_check_ruleset_version")
                ),
                requestId, reservationOwner, now
        );
        if (decisions.isEmpty()) {
            throw new TurnConflictException("턴 reservation이 만료되었거나 회수되었습니다.");
        }

        StoredDecision stored = decisions.getFirst();
        if (stored.isComplete()) {
            return stored.toDomain();
        }
        if (!stored.isEmpty()) {
            throw new IllegalStateException("저장된 Skill Check 판정이 부분적으로 손상되었습니다.");
        }

        SkillCheckResult created = Objects.requireNonNull(decisionFactory.get(), "SkillCheckResult는 필수입니다.");
        int updated = jdbcTemplate.update(
                """
                        UPDATE game_turn_reservation
                        SET skill_check_stat_type = ?, skill_check_raw_roll = ?, skill_check_stat_modifier = ?,
                            skill_check_situational_modifier = ?, skill_check_dc = ?, skill_check_total = ?,
                            skill_check_outcome = ?, skill_check_ruleset_version = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND lease_owner = ? AND lease_expires_at > ?
                          AND skill_check_stat_type IS NULL
                        """,
                created.statType().name(), created.rawRoll(), created.statModifier(),
                created.situationalModifier(), created.dc(), created.total(), created.outcome().name(),
                created.rulesetVersion(), requestId, reservationOwner, now
        );
        if (updated != 1) {
            throw new TurnConflictException("Skill Check 판정을 reservation에 확정할 수 없습니다.");
        }
        return created;
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record StoredDecision(
            String statType,
            Integer rawRoll,
            Integer statModifier,
            Integer situationalModifier,
            Integer dc,
            Integer total,
            String outcome,
            Integer rulesetVersion
    ) {
        private boolean isEmpty() {
            return statType == null && rawRoll == null && statModifier == null && situationalModifier == null
                    && dc == null && total == null && outcome == null && rulesetVersion == null;
        }

        private boolean isComplete() {
            return statType != null && rawRoll != null && statModifier != null && situationalModifier != null
                    && dc != null && total != null && outcome != null && rulesetVersion != null;
        }

        private SkillCheckResult toDomain() {
            try {
                return new SkillCheckResult(
                        StatType.valueOf(statType),
                        rawRoll,
                        statModifier,
                        situationalModifier,
                        dc,
                        total,
                        SkillCheckOutcome.valueOf(outcome),
                        rulesetVersion
                );
            } catch (RuntimeException exception) {
                throw new IllegalStateException("저장된 Skill Check 판정이 현재 규칙과 일치하지 않습니다.", exception);
            }
        }
    }
}
