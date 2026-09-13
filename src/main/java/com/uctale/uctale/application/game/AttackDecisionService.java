package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.AttackResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class AttackDecisionService {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    public AttackDecisionService(JdbcTemplate jdbcTemplate, Clock clock, ObjectMapper objectMapper) { this.jdbcTemplate = jdbcTemplate; this.clock = clock; this.objectMapper = objectMapper; }

    @Transactional
    public AttackResult getOrCreate(Long requestId, String reservationOwner, Supplier<AttackResult> decisionFactory) {
        if (requestId == null || reservationOwner == null || reservationOwner.isBlank()) throw new IllegalArgumentException("Attack 판정에는 유효한 reservation 소유권이 필요합니다.");
        Objects.requireNonNull(decisionFactory, "decisionFactory는 필수입니다.");
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> decisions = jdbcTemplate.query("""
                        SELECT attack_result_json FROM game_turn_reservation
                        WHERE request_id = ? AND lease_owner = ? AND lease_expires_at > ? FOR UPDATE
                        """, (rs, rowNum) -> rs.getString("attack_result_json"), requestId, reservationOwner, now);
        if (decisions.isEmpty()) throw new TurnConflictException("턴 reservation이 만료되었거나 회수되었습니다.");
        String stored = decisions.getFirst();
        if (stored != null) { if (stored.isBlank()) throw new IllegalStateException("저장된 Attack 판정이 손상되었습니다."); return deserialize(stored); }
        AttackResult created = Objects.requireNonNull(decisionFactory.get(), "AttackResult는 필수입니다.");
        int updated = jdbcTemplate.update("""
                        UPDATE game_turn_reservation SET attack_result_json = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND lease_owner = ? AND lease_expires_at > ?
                        """, serialize(created), requestId, reservationOwner, now);
        if (updated != 1) throw new TurnConflictException("Attack 판정을 reservation에 확정할 수 없습니다.");
        return created;
    }
    private String serialize(AttackResult result) { try { return objectMapper.writeValueAsString(result); } catch (JacksonException e) { throw new IllegalStateException("Attack 판정 직렬화에 실패했습니다.", e); } }
    private AttackResult deserialize(String json) { try { return objectMapper.readValue(json, AttackResult.class); } catch (JacksonException | IllegalArgumentException e) { throw new IllegalStateException("저장된 Attack 판정이 현재 규칙과 일치하지 않습니다.", e); } }
}
