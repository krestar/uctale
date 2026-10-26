package com.uctale.uctale.application.game;

import com.uctale.uctale.dto.SessionPageResponse;
import com.uctale.uctale.dto.SessionSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * One bounded, owner-scoped metadata projection. Never fetches or deserializes state_json.
 * Resume performs the deeper canonical validation before allowing a player to act.
 */
@Component
public class SessionLibraryQuery {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    private static final int FALLBACK_TITLE_LENGTH = 80;

    // The order must match the pagination index and cursor predicate.
    // One SQL statement per page, independent of the number of sessions returned.
    static final String PAGE_SQL = """
            SELECT s.id, s.world_setting, s.current_turn, s.updated_at,
                   l.turn_number AS log_turn, l.state_version AS log_state_version,
                   l.image_url,
                   (SELECT m.result_title FROM game_mutation_request m
                    WHERE m.owner_key = s.owner_key AND m.result_session_id = s.id
                      AND m.result_turn = s.current_turn AND m.status = 'COMPLETED'
                    ORDER BY m.updated_at DESC, m.id DESC LIMIT 1) AS result_title,
                   (SELECT m.status FROM game_mutation_request m
                    WHERE m.owner_key = s.owner_key AND m.session_id = s.id
                      AND m.expected_turn = s.current_turn AND m.operation = 'PROGRESS'
                    ORDER BY m.updated_at DESC, m.id DESC LIMIT 1) AS request_status,
                   r.lease_expires_at, r.recovery_available_at
            FROM game_session s
            LEFT JOIN game_log l ON l.session_id = s.id
                AND l.turn_number = (SELECT MAX(last_log.turn_number)
                                     FROM game_log last_log WHERE last_log.session_id = s.id)
            LEFT JOIN game_turn_reservation r ON r.session_id = s.id
                AND r.expected_turn = s.current_turn
            WHERE s.owner_key = ?
            %s
            ORDER BY s.updated_at DESC NULLS LAST, s.id DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SessionLibraryQuery(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public SessionPageResponse list(String ownerKey, String cursor, int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size는 1부터 " + MAX_SIZE + " 사이여야 합니다.");
        }
        Position after = decode(cursor);
        String predicate = "";
        List<Object> args = new ArrayList<>();
        args.add(ownerKey);
        if (after != null) {
            if (after.updatedAt() == null) {
                predicate = "AND s.updated_at IS NULL AND s.id < ?";
                args.add(after.id());
            } else {
                predicate = "AND (s.updated_at < ? OR (s.updated_at = ? AND s.id < ?) OR s.updated_at IS NULL)";
                args.add(after.updatedAt());
                args.add(after.updatedAt());
                args.add(after.id());
            }
        }
        args.add(size + 1);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Row> rows = jdbc.query(PAGE_SQL.formatted(predicate), (rs, n) -> {
            var updatedAt = rs.getTimestamp("updated_at");
            var lease = rs.getTimestamp("lease_expires_at");
            var recovery = rs.getTimestamp("recovery_available_at");
            int logTurn = rs.getInt("log_turn");
            boolean hasLog = !rs.wasNull();
            int logStateVersion = rs.getInt("log_state_version");
            boolean hasLogStateVersion = !rs.wasNull();
            return new Row(
                    rs.getLong("id"), rs.getString("world_setting"), rs.getInt("current_turn"),
                    updatedAt == null ? null : updatedAt.toLocalDateTime(),
                    hasLog ? logTurn : null, hasLogStateVersion ? logStateVersion : null,
                    rs.getString("image_url"), rs.getString("result_title"),
                    rs.getString("request_status"),
                    lease == null ? null : lease.toLocalDateTime(),
                    recovery == null ? null : recovery.toLocalDateTime()
            );
        }, args.toArray());

        boolean hasMore = rows.size() > size;
        List<Row> page = hasMore ? rows.subList(0, size) : rows;
        List<SessionSummaryResponse> items = page.stream().map(row -> summarize(row, now)).toList();
        String next = hasMore ? encode(page.getLast()) : null;
        return new SessionPageResponse(items, next, hasMore);
    }

    private SessionSummaryResponse summarize(Row row, LocalDateTime now) {
        String title = row.resultTitle() == null || row.resultTitle().isBlank()
                ? fallbackTitle(row.worldSetting()) : row.resultTitle();
        String thumbnail = row.imageUrl() == null || row.imageUrl().isBlank()
                ? null : row.imageUrl().endsWith("/thumbnail") ? row.imageUrl() : row.imageUrl() + "/thumbnail";

        String status;
        String message;
        boolean retryable = false;
        boolean canResume = true;
        Long retryAfterSeconds = null;
        if (row.logTurn() == null || row.logTurn() != row.currentTurn()
                || row.logStateVersion() == null || row.logStateVersion() != row.currentTurn()) {
            status = "UNRECOVERABLE";
            message = "마지막 완료 턴의 기록이 일치하지 않습니다. 데이터를 변경하지 않았습니다.";
            canResume = false;
        } else if (row.leaseExpiresAt() != null && row.leaseExpiresAt().isAfter(now)) {
            status = "PROCESSING";
            message = "새 턴을 처리 중입니다. 마지막 완료 턴은 보존되어 있습니다.";
        } else if (row.recoveryAvailableAt() != null && row.recoveryAvailableAt().isAfter(now)) {
            status = "RECOVERY_WAIT";
            message = "Narrative provider 복구를 기다리고 있습니다.";
            retryable = true;
            retryAfterSeconds = Math.max(1L, ChronoUnit.SECONDS.between(now, row.recoveryAvailableAt()));
        } else if ("FAILED".equals(row.requestStatus()) || "PROCESSING".equals(row.requestStatus())) {
            status = "FAILED";
            message = "이전 진행 요청이 완료되지 않았습니다. 마지막 완료 턴에서 다시 선택할 수 있습니다.";
            retryable = true;
        } else {
            status = "READY";
            message = "마지막 완료 턴에서 계속할 수 있습니다.";
        }
        return new SessionSummaryResponse(row.id(), title, row.currentTurn(), row.updatedAt(),
                status, message, retryable, canResume, retryAfterSeconds, thumbnail);
    }

    private static String fallbackTitle(String worldSetting) {
        if (worldSetting == null || worldSetting.isBlank()) return "저장된 이야기";
        String line = worldSetting.strip().lines().findFirst().orElse("저장된 이야기").strip();
        return line.length() <= FALLBACK_TITLE_LENGTH
                ? line : line.substring(0, FALLBACK_TITLE_LENGTH - 1) + "…";
    }

    private static String encode(Row row) {
        String value = (row.updatedAt() == null ? "null" : row.updatedAt().toString()) + "|" + row.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Position decode(String cursor) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 256) {
            throw new IllegalArgumentException("잘못된 세션 목록 cursor입니다.");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            long id = Long.parseLong(parts[1]);
            if (id < 1) throw new IllegalArgumentException();
            LocalDateTime at = "null".equals(parts[0]) ? null : LocalDateTime.parse(parts[0]);
            return new Position(at, id);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("잘못된 세션 목록 cursor입니다.");
        }
    }

    private record Position(LocalDateTime updatedAt, long id) {}
    private record Row(long id, String worldSetting, int currentTurn, LocalDateTime updatedAt,
                       Integer logTurn, Integer logStateVersion, String imageUrl, String resultTitle,
                       String requestStatus, LocalDateTime leaseExpiresAt, LocalDateTime recoveryAvailableAt) {}
}
