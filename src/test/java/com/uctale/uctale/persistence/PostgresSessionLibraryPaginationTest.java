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

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PostgresSessionLibraryPaginationTest extends PostgresIntegrationTestSupport {
    private static final String OWNER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Autowired JdbcTemplate jdbc;
    @Autowired GameSessionQueryService service;
    @Autowired DataSource dataSource;
    @Autowired Clock clock;

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
    void listOnlyReadsMetadataAndHasIndexSupportedPlan() {
        seed(80, OWNER);
        // EXPLAIN is measured against the real PostgreSQL query rather than an estimated ORM traversal.
        String plan = String.join("\n", jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT id FROM game_session WHERE owner_key = ?
                ORDER BY updated_at DESC NULLS LAST, id DESC LIMIT 11
                """, String.class, OWNER));
        assertThat(plan).contains("Limit");
        String indexDefinition = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes WHERE tablename='game_session'
                AND indexname='idx_game_session_owner_updated_id'
                """, String.class);
        assertThat(indexDefinition).contains("owner_key").contains("updated_at DESC NULLS LAST").contains("id DESC");
        var page = service.listSessionsPage(OWNER, null, 10);
        assertThat(page.items()).hasSize(10);
        assertThat(page.hasMore()).isTrue();
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
