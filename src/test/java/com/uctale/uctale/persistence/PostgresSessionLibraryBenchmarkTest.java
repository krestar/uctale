package com.uctale.uctale.persistence;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameSessionQueryService;
import com.uctale.uctale.application.game.GameStateCodec;
import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import com.uctale.uctale.repository.GameStateSnapshotRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repeatable, local PostgreSQL measurement of the pre-#150 list access pattern
 * and current production projection. This intentionally never uses a production DB.
 * Timings are observations, not correctness thresholds.
 */
@SpringBootTest
class PostgresSessionLibraryBenchmarkTest extends PostgresIntegrationTestSupport {
    private static final String OWNER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER_OWNER = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final Path REPORT = Path.of("build/reports/session-library-benchmark.md");
    private static final int MEASURED_RUNS = 5;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private GamePersistenceService persistence;
    @Autowired private GameSessionQueryService queryService;
    @Autowired private GameSessionRepository sessions;
    @Autowired private GameLogRepository logs;
    @Autowired private GameMutationRequestRepository mutations;
    @Autowired private GameStateSnapshotRepository snapshots;
    @Autowired private GameStateCodec stateCodec;
    @Autowired private ChoiceCodec choiceCodec;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void comparePreviousListPatternAndCurrentProjection() throws IOException {
        jdbc.execute("""
                TRUNCATE TABLE game_turn_reservation, game_mutation_request, image_asset,
                    game_state_snapshot, game_log, game_session RESTART IDENTITY
                """);
        GameSession seed = persistence.saveOpening(OWNER, "도시", "탐험가", "첫 장면",
                choiceCodec.serialize(List.of(new GameChoice(1, "다음"))), null);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            List<Sample> samples = new ArrayList<>();
            int total = 1;
            for (int target : new int[] {20, 50, 100}) {
                insertMoreSessions(target - total, seed.getId());
                total = target;
                // Interleave runs; do not convert noisy wall times to a CI pass/fail assertion.
                runLegacy(statistics, target);
                runPaged(statistics, target);
                List<Long> legacyTimes = new ArrayList<>();
                List<Long> pagedTimes = new ArrayList<>();
                int legacyQueries = -1;
                int pagedQueries = -1;
                for (int run = 0; run < MEASURED_RUNS; run++) {
                    if (run % 2 == 0) {
                        Observation old = runLegacy(statistics, target);
                        Observation now = runPaged(statistics, target);
                        legacyTimes.add(old.elapsedNanos());
                        pagedTimes.add(now.elapsedNanos());
                        legacyQueries = old.sqlStatements();
                        pagedQueries = now.sqlStatements();
                    } else {
                        Observation now = runPaged(statistics, target);
                        Observation old = runLegacy(statistics, target);
                        legacyTimes.add(old.elapsedNanos());
                        pagedTimes.add(now.elapsedNanos());
                        legacyQueries = old.sqlStatements();
                        pagedQueries = now.sqlStatements();
                    }
                }
                assertThat(legacyQueries).isGreaterThan(pagedQueries);
                assertThat(pagedQueries).isEqualTo((target + 49) / 50);
                samples.add(new Sample(target, legacyQueries, pagedQueries,
                        medianMillis(legacyTimes), medianMillis(pagedTimes)));
            }

            // Explain on a larger, representative layout to expose the owner+sort index choice.
            insertMoreSessions(2900, seed.getId());
            jdbc.update("""
                    INSERT INTO game_session(owner_key,world_setting,character_setting,
                      is_game_over,current_turn,version,created_at,updated_at)
                    SELECT ?, '타 세계', '다른 인물', false, 1, 0,
                      TIMESTAMP '2026-01-01', TIMESTAMP '2026-01-01' + n * INTERVAL '1 second'
                    FROM generate_series(1,3000) n
                    """, OTHER_OWNER);
            jdbc.update("""
                    INSERT INTO game_log(session_id,turn_number,previous_state_version,state_version)
                    SELECT id,1,0,1 FROM game_session WHERE owner_key=?
                    """, OTHER_OWNER);
            jdbc.execute("ANALYZE game_session");
            jdbc.execute("ANALYZE game_log");
            String actualSql = (String) ReflectionTestUtils.getField(
                    com.uctale.uctale.application.game.SessionLibraryQuery.class, "PAGE_SQL");
            assertThat(actualSql).isNotBlank();
            String beforeList = explain("""
                    SELECT id FROM game_session WHERE owner_key=?
                    ORDER BY updated_at DESC NULLS LAST, id DESC
                    """, OWNER);
            String after = explain(actualSql.formatted(""), OWNER, 21);
            assertThat(after).contains("Execution Time:")
                    .contains("idx_game_session_owner_updated_id");

            StringBuilder report = new StringBuilder("""
                    # Session Library PostgreSQL 실측 (Issue #130)

                    - **측정 대상:** GitHub Actions의 Testcontainers PostgreSQL 17.6-alpine, Flyway V22 적용 상태. 운영 DB 데이터·자격증명 사용 없음.
                    - **비교:** PR #150 이전 `GameSessionQueryService.listSessions`의 repository 호출 순서를 이 테스트에서 재현한 **legacy 경로** vs 현재 `listSessionsPage` **실제 서비스 호출**.
                    - legacy는 목록 전체를 로드한 뒤 각 세션에 대해 최신 log, 완료 제목, snapshot deserialize, reservation SQL, 최신 progress request를 조회한다. provider 호출 없음.
                    - DB 요청 횟수: legacy는 Hibernate `Statistics.prepareStatementCount` + 실제 실행한 JDBC reservation 조회 수, 현재 구현은 JDBC projection 호출 수. 준비/실행한 SQL statement 횟수이며 DB 내부 index scan 수가 아니다.
                    - **범위:** owner session 20 / 50 / 100, 첫 완료 turn의 정상 snapshot/log, 미진행 reservation/mutation. 100건은 현재 API 한도 50건 기준 2페이지를 이어서 조회한다.
                    - **측정법:** 시나리오당 1회 warm-up 이후 5회 측정의 Java 실행 경과 시간 중앙값, 이전/이후 순서 교대, 같은 CI runner/DB 사용. 밀리초 수치는 CI 환경·cache/JIT 영향을 받으므로 추세 참고용이며 지연시간 성능 보장은 아니다.

                    ## 동일 데이터의 실제 SQL 실행 횟수 및 서비스 경과 시간

                    | owner 세션 | legacy SQL statements | 현재 SQL statements | legacy 중앙값(ms) | 현재 중앙값(ms) |
                    | ---: | ---: | ---: | ---: | ---: |
                    """);
            for (Sample sample : samples) {
                report.append(String.format(Locale.ROOT, "| %d | %d | %d | %.3f | %.3f |%n",
                        sample.sessions(), sample.legacySql(), sample.pagedSql(),
                        sample.legacyMedianMs(), sample.pagedMedianMs()));
            }
            report.append("""
                    
                    ## PostgreSQL EXPLAIN (ANALYZE, BUFFERS)

                    아래 plan은 **총 6,000 세션(owner 3,000 / 다른 owner 3,000)** 및 각 owner의 최신 game_log 1행 조건으로 생성했다. legacy는 목록 최초 SELECT만 EXPLAIN한 것이며, 나머지 세션별 SQL은 위 실행 횟수에 포함된다. 개선 후 plan은 현재 `SessionLibraryQuery.PAGE_SQL` 전체를 그대로 실행한 결과다. 개별 timing을 합산해 HTTP end-to-end 지연시간으로 해석하지 않는다.

                    ### Legacy 세션 ID 목록 SELECT

                    ```text
                    """).append(beforeList).append("\n```\n\n### 현재 전체 목록 projection (LIMIT 21)\n\n```text\n")
                    .append(after).append("\n```\n\n");
            report.append("""
                    ## 해석 및 한계

                    - 실제 query count는 기존 패턴에서 세션 개수에 따라 증가하지만 새 projection은 페이지당 1회이다. snapshot deserialize는 새 목록 API에 없다.
                    - 수치의 legacy 대상은 **현재 테스트에서 재현한 이전 코드 경로**다. 과거 production 요청을 기록한 telemetry가 아니며 네트워크, HTTP, React 렌더링, production pool/부하를 포함하지 않는다.
                    - 인덱스 사용은 PostgreSQL planner의 이 fixture에 대한 선택 결과다. 데이터 분포에 따라 다른 실행 계획이 가능하다.
                    - 재현: `./gradlew postgresIntegrationTest --tests '*PostgresSessionLibraryBenchmarkTest'` (Docker 필요). 결과 파일은 `build/reports/session-library-benchmark.md`.
                    """);
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        } finally {
            statistics.setStatisticsEnabled(previous);
        }
    }

    private Observation runLegacy(Statistics statistics, int expectedSize) {
        statistics.clear();
        long begin = System.nanoTime();
        List<GameSession> all = sessions.findByOwnerKeyOrderByUpdatedAtDesc(OWNER);
        assertThat(all).hasSize(expectedSize);
        int reservationStatements = 0;
        for (GameSession session : all) {
            var log = logs.findTopByGameSessionOrderByTurnNumberDesc(session).orElseThrow();
            mutations.findTopByOwnerKeyAndResultSessionIdAndResultTurnAndStatusOrderByUpdatedAtDesc(
                    OWNER, session.getId(), session.getCurrentTurn(), GameMutationRequest.Status.COMPLETED);
            var state = snapshots.findById(session.getId())
                    .map(snapshot -> stateCodec.deserialize(snapshot.getStateJson())).orElseThrow();
            assertThat(state.turnNumber()).isEqualTo(session.getCurrentTurn());
            assertThat(log.getStateVersion()).isEqualTo(session.getCurrentTurn());
            choiceCodec.deserialize(log.getChoicesJson());
            jdbc.query("""
                    SELECT lease_expires_at, provider_attempt_count, recovery_available_at
                    FROM game_turn_reservation WHERE session_id=? AND expected_turn=?
                    """, (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(), session.getId(), session.getCurrentTurn());
            reservationStatements++;
            mutations.findTopByOwnerKeyAndSessionIdAndExpectedTurnAndOperationOrderByUpdatedAtDesc(
                    OWNER, session.getId(), session.getCurrentTurn(), "PROGRESS");
        }
        long elapsed = System.nanoTime() - begin;
        long count = statistics.getPrepareStatementCount() + reservationStatements;
        return new Observation(Math.toIntExact(count), elapsed);
    }

    private Observation runPaged(Statistics statistics, int expectedSize) {
        statistics.clear();
        long start = System.nanoTime();
        String cursor = null;
        int rows = 0;
        int queries = 0;
        do {
            var page = queryService.listSessionsPage(OWNER, cursor, 50);
            rows += page.items().size();
            queries++;
            cursor = page.nextCursor();
            if (!page.hasMore()) break;
        } while (true);
        long elapsed = System.nanoTime() - start;
        assertThat(rows).isEqualTo(expectedSize);
        // No JPA reads or snapshot upgrades in this path.
        assertThat(statistics.getPrepareStatementCount()).isZero();
        return new Observation(queries, elapsed);
    }

    private void insertMoreSessions(int count, long exemplarId) {
        if (count == 0) return;
        jdbc.update("""
                INSERT INTO game_session(owner_key,world_setting,character_setting,
                  is_game_over,current_turn,version,created_at,updated_at)
                SELECT ?, '도시', '탐험가', false, 1, 0,
                  TIMESTAMP '2026-01-01', TIMESTAMP '2026-01-01' + n * INTERVAL '1 second'
                FROM generate_series(1,?) n
                """, OWNER, count);
        jdbc.update("""
                INSERT INTO game_log(session_id,turn_number,previous_state_version,
                  state_version,story_text,choices_json,image_url)
                SELECT s.id,1,0,1, seed.story_text, seed.choices_json, seed.image_url
                FROM game_session s CROSS JOIN game_log seed
                WHERE seed.session_id=? AND s.owner_key=?
                  AND NOT EXISTS(SELECT 1 FROM game_log l WHERE l.session_id=s.id)
                """, exemplarId, OWNER);
        jdbc.update("""
                INSERT INTO game_state_snapshot(session_id,state_json,updated_at)
                SELECT s.id, seed.state_json, CURRENT_TIMESTAMP
                FROM game_session s CROSS JOIN game_state_snapshot seed
                WHERE seed.session_id=? AND s.owner_key=?
                  AND NOT EXISTS(SELECT 1 FROM game_state_snapshot snap WHERE snap.session_id=s.id)
                """, exemplarId, OWNER);
    }

    private String explain(String sql, Object... parameters) {
        List<String> lines = jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class, parameters);
        return String.join("\n", lines);
    }

    private double medianMillis(List<Long> nanos) {
        Collections.sort(nanos);
        return nanos.get(nanos.size() / 2) / 1_000_000.0;
    }

    private record Observation(int sqlStatements, long elapsedNanos) {}
    private record Sample(int sessions, int legacySql, int pagedSql,
                          double legacyMedianMs, double pagedMedianMs) {}
}
