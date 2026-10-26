package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.GameSessionQueryService;
import com.uctale.uctale.application.game.SessionLibraryQuery;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PostgresSessionLibraryPaginationTest extends PostgresIntegrationTestSupport {
    private static final Logger log = LoggerFactory.getLogger(PostgresSessionLibraryPaginationTest.class);
    private static final String OWNER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired JdbcTemplate jdbc;
    @Autowired GameSessionQueryService service;
    @Autowired DataSource dataSource;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate table game_turn_reservation, game_mutation_request, image_asset, game_state_snapshot, game_log, game_session restart identity");
    }

    @Test
    void pageIsBoundedOwnerScopedAndStableAcrossTimestampTies() {
        seed(65, OWNER);
        seed(3, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 7; page++) {
            var result = service.listSessionsPage(OWNER, cursor, 10);
            assertThat(result.items()).hasSize(page == 6 ? 5 : 10);
            result.items().forEach(row -> {
                ids.add(row.sessionId());
                assertThat(row.status()).isEqualTo("READY");
                assertThat(row.canResume()).isTrue();
            });
            if (page < 6) {
                assertThat(result.hasMore()).isTrue();
                assertThat(result.nextCursor()).isNotBlank();
            } else {
                assertThat(result.hasMore()).isFalse();
                assertThat(result.nextCursor()).isNull();
            }
            cursor = result.nextCursor();
        }
        assertThat(ids).doesNotHaveDuplicates().hasSize(65);
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThatThrownBy(() -> service.listSessionsPage(OWNER, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listSessionsPage(OWNER, null, 51)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listSessionsPage(OWNER, "wrong", 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleDatabaseStatementPerPageIndependentOfSessionCount() {
        seed(65, OWNER);
        class CountingJdbc extends JdbcTemplate {
            int queries;
            CountingJdbc(DataSource source) { super(source); }
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                queries++;
                return super.query(sql, mapper, args);
            }
        }
        CountingJdbc counting = new CountingJdbc(dataSource);
        var query = new SessionLibraryQuery(counting, clock);
        assertThat(query.list(OWNER, null, 1).items()).hasSize(1);
        assertThat(counting.queries).isEqualTo(1);
        assertThat(query.list(OWNER, null, 50).items()).hasSize(50);
        assertThat(counting.queries).isEqualTo(2);
    }

    @Test
    void fullProjectionExplainHasActualIndexPlanAndBeforeAfterEvidence() {
        // Large enough to make a realistic top-N index scan attractive without forcing planner switches.
        jdbc.update("""
                INSERT INTO game_session(owner_key, world_setting, character_setting, is_game_over,
                  current_turn, version, updated_at, created_at)
                SELECT CASE WHEN n <= 3000 THEN ? ELSE 'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB' END,
                  '세계', '인물', false, 1, 0, TIMESTAMP '2026-01-01' + n * INTERVAL '1 second', TIMESTAMP '2026-01-01'
                FROM generate_series(1, 6000) n
                """, OWNER);
        jdbc.update("""
                INSERT INTO game_log(session_id, turn_number, previous_state_version, state_version)
                SELECT id, 1, 0, 1 FROM game_session
                """);
        jdbc.execute("ANALYZE game_session");
        jdbc.execute("ANALYZE game_log");
        String indexDefinition = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND tablename='game_session'
                  AND indexname='idx_game_session_owner_updated_id'
                """, String.class);
        assertThat(indexDefinition).contains("owner_key").contains("updated_at DESC NULLS LAST").contains("id DESC");

        // Introspect the actual production SQL: an unrelated SELECT id query would not measure
        // projection joins/subqueries or the real ordering/limit.
        String productionSql = (String) ReflectionTestUtils.getField(SessionLibraryQuery.class, "PAGE_SQL");
        assertThat(productionSql).isNotBlank();
        String explainSql = "EXPLAIN (ANALYZE, BUFFERS) " + productionSql.formatted("");

        List<String> before = new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("DROP INDEX idx_game_session_owner_updated_id");
            List<String> result = jdbc.queryForList(explainSql, String.class, OWNER, 21);
            status.setRollbackOnly();
            return result;
        });
        // The transaction rolls back the DDL; the real application index must still exist.
        List<String> after = jdbc.queryForList(explainSql, String.class, OWNER, 21);
        String beforePlan = String.join("\\n", before);
        String afterPlan = String.join("\\n", after);
        log.info("Session Library SQL plan WITHOUT new ordering index (baseline; ANALYZE):\\n{}", beforePlan);
        log.info("Session Library SQL plan WITH new ordering index (ANALYZE):\\n{}", afterPlan);
        assertThat(beforePlan).contains("Execution Time:").contains("Limit");
        assertThat(afterPlan).contains("Execution Time:").contains("Limit")
                .contains("idx_game_session_owner_updated_id");
        assertThat(service.listSessionsPage(OWNER, null, 20).items()).hasSize(20);
    }

    private void seed(int n, String owner) {
        for (int i = 0; i < n; i++) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO game_session(owner_key, world_setting, character_setting, is_game_over,
                      current_turn, version, updated_at, created_at)
                    VALUES (?, '세계', '인물', false, 1, 0, '2026-01-01', '2026-01-01') RETURNING id
                    """, Long.class, owner);
            jdbc.update("""
                    INSERT INTO game_log(session_id, turn_number, previous_state_version, state_version)
                    VALUES (?, 1, 0, 1)
                    """, id);
        }
    }
}
