# Issue #130 — Session Library PostgreSQL 실측

측정일: 2026-10-27 (KST). 근거: [PR #151 CI run #40360701473](https://github.com/krestar/uctale/actions/runs/40360701473), artifact `session-library-benchmark`의 원본 `session-library-benchmark.md`. PR #150의 성능 개선 후 동일 데이터베이스에서 이전 코드의 repository 조회 패턴을 테스트로 재구성하여 비교했다.

## 방법

- GitHub Actions Ubuntu runner, Testcontainers PostgreSQL **17.6-alpine**, Flyway **V22** 적용. 운영 DB 접근/외부 provider 호출 없음.
- 이전 경로: PR #150 전 `GameSessionQueryService.listSessions`의 repository 호출 흐름(세션 전체 목록, 최신 log, mutation title, snapshot 역직렬화, reservation, progress request)을 테스트에서 재구성했다. **과거 production telemetry가 아니며 과거 commit 자체의 binary를 실행한 것도 아니다.**
- 현재 경로: production `GameSessionQueryService.listSessionsPage(ownerKey, cursor, 50)` 직접 호출. 100개 세션은 50개씩 두 페이지 조회.
- SQL count: 기존은 Hibernate `Statistics.prepareStatementCount` + repository 외 별도 `JdbcTemplate` reservation SQL 호출 수. 현재는 페이지당 `JdbcTemplate` 호출 수를 검증한 값. DB 내부 index scan 횟수와는 다르다.
- 경과 시간: 같은 runner/DB에서 warm-up 1회 후 각 5회 측정, 실행 순서를 교차, `System.nanoTime` 중앙값. 지연시간 수치는 캐시·JIT·runner 부하에 민감한 관측값이며 성능 보장 아님.
- fixture: owner별 20/50/100개, 정상 첫 완료 turn log/snapshot, reservation 및 mutation 없음. 모든 비교에 동일 owner 데이터를 사용한다.

## 측정 결과

| owner 세션 | 과거 조회 패턴 SQL statements | 현재 SQL statements | 과거 경과 시간 중앙값(ms) | 현재 경과 시간 중앙값(ms) |
| ---: | ---: | ---: | ---: | ---: |
| 20 | 101 | 1 | 69.025 | 2.966 |
| 50 | 251 | 1 | 153.307 | 3.169 |
| 100 | 501 | 2 | 248.573 | 5.569 |

세션 100개 기준 JDBC/Hibernate SQL 실행 횟수는 501 → 2 (**약 250.5분의 1**)이고, 해당 CI 경과 시간 관측은 248.573 → 5.569ms (**약 44.6배**)다. 전체 세션을 제한 없이 불러오던 과거 API와 달리 신규 API는 서버측 50개 제한이며, 전체 100개를 얻기 위해 두 페이지를 순차 조회했다. 현재 목록 경로에서 snapshot deserialize는 없다.

## 실제 PostgreSQL 실행 계획

6,000개 세션(owner 3,000개, 타 owner 3,000개)과 각 최신 log 1행을 가진 fixture에 `ANALYZE` 실행 후 `EXPLAIN (ANALYZE, BUFFERS)` 측정.

- 과거 목록 최초 SELECT(`game_session` 전체 owner 조회, 무제한): `Seq Scan on game_session` → `Sort`; `rows=3000`, `Execution Time: 1.615 ms`, `Buffers: shared hit=96`. **이 SQL만의 시간이며 세션별 나머지 SQL은 제외**된다.
- 현재 `SessionLibraryQuery.PAGE_SQL` 전체 projection (`LIMIT 21`, size 20 + lookahead): `Index Scan using idx_game_session_owner_updated_id on game_session s`; 21행, `Execution Time: 0.350 ms`, `Buffers: shared hit=257`.
- 현재 projection의 나머지 주요 plan: `idx_game_log_session_turn`(latest game log), `game_turn_reservation_pkey`, `idx_mutation_session_title_lookup`, `idx_mutation_session_progress_lookup`의 각 index scan 확인.
- 0.350 ms와 1.615 ms를 곧바로 HTTP 성능 비교로 해석하면 안 된다. 과거 쿼리는 목록 첫 SELECT만 측정한 것이고 현재 쿼리는 전체 projection이며 반환 row 수 또한 다르다. 위 서비스 경과 시간과 SQL 횟수를 별도 근거로 확인한다.

정확한 두 실행 계획의 노드·loops·planning/buffers 전체는 [CI artifact](https://github.com/krestar/uctale/actions/runs/40360701473)의 `session-library-benchmark.md` 참고.

## 재현 및 한계

```bash
./gradlew postgresIntegrationTest --tests '*PostgresSessionLibraryBenchmarkTest'
cat build/reports/session-library-benchmark.md
```

Docker-compatible runtime이 필요하다. 실행 결과는 Testcontainers DB에만 저장되며 runtime 또는 production canonical 상태를 변경하지 않는다. 동일 계측 환경의 측정이지만, data 분포가 상이한 production에서의 실제 응답 시간, 여러 턴/실패 세션, 네트워크/HTTP, 프론트엔드 렌더링 및 동시 부하를 포함하지 않는다. 시간 측정값을 CI의 성공/실패 threshold로 사용하지 않고 쿼리 수·결과 정합성으로 회귀를 검증한다.
