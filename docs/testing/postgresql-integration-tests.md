# PostgreSQL integration tests

UCTale의 기본 backend test suite는 빠른 회귀 검증을 위해 H2를 사용합니다. production PostgreSQL의 unique constraint, optimistic locking, Flyway migration, 동시성·lease 의미론은 별도 Testcontainers suite에서 검증합니다.

## 실행 명령

기본 unit / H2 suite:

```bash
./gradlew clean test
```

PostgreSQL integration suite:

```bash
./gradlew postgresIntegrationTest
```

PostgreSQL suite는 Testcontainers를 사용하므로 로컬에서 Docker-compatible container runtime이 실행 중이어야 합니다. production Neon DB나 별도 shared test DB에는 연결하지 않습니다.

GitHub Actions도 같은 `./gradlew postgresIntegrationTest` 명령을 사용합니다. GitHub-hosted Ubuntu runner의 Docker daemon에서 ephemeral PostgreSQL container를 띄우므로 별도 database secret은 필요하지 않습니다.

## 선택한 방식

#73에서 GitHub Actions의 PostgreSQL service container 대신 Testcontainers를 선택했습니다.

- 로컬과 CI가 동일한 Java test code와 database image를 사용합니다.
- 포트, username, password를 workflow와 로컬 설정에 중복 정의하지 않습니다.
- persistence test가 공통 support class를 상속해 같은 환경을 재사용합니다.
- 테스트가 production Neon DB에 연결될 가능성을 구조적으로 줄입니다.

Testcontainers BOM은 `2.0.5`, PostgreSQL image는 `postgres:17.6-alpine`으로 고정합니다. 버전 변경은 일반 dependency/image update처럼 PR에서 검증합니다.

## 테스트 경계

JUnit tag `postgres`를 경계로 사용합니다.

- `test`: `postgres` tag를 제외한 H2/unit suite
- `postgresIntegrationTest`: `postgres` tag만 실행
- PostgreSQL 테스트는 `PostgresIntegrationTestSupport`를 상속해 datasource/Flyway/JPA 설정을 공유

현재 baseline은 다음 production 의미론을 검증합니다.

- 빈 PostgreSQL에서 Flyway V1~V10 migration chain 순차 적용
- Hibernate `ddl-auto=validate`와 현재 entity mapping
- `uk_game_log_session_turn` unique constraint
- `GameSession` optimistic locking
- owner 범위 `Idempotency-Key` uniqueness와 fingerprint conflict
- `(session_id, expected_turn)` reservation unique/lease takeover
- `provider_attempt_count` 기반 turn당 provider attempt 최대 3회
- pre-provider 실패가 provider attempt quota를 소비하지 않는 계약
- versioned `GameState` snapshot의 legacy 호환 및 latest schema round-trip
- PostgreSQL 기반 provider usage ledger

## M2 regression matrix

M2 종료 게이트는 기존 개별 persistence 테스트를 유지하면서 `PostgresM2TurnIntegrityMatrixTest`와 migration fixture로 cross-feature 경계를 고정합니다.

| 계약 | PostgreSQL 검증 |
| --- | --- |
| `/init` completed retry | 동일 key 재시도는 저장된 session/turn을 재사용하고 provider 및 새 session 생성을 반복하지 않음 |
| `/progress` completed retry | 동일 key 재시도는 저장 결과를 재사용하고 canonical GameLog/snapshot을 다시 쓰지 않음 |
| 동일 key + 다른 payload | fingerprint mismatch를 provider 진입 전에 conflict로 거부 |
| 같은 session/turn 최초 동시 요청 | active reservation은 하나만 유지되고 유효 lease 동안 provider 진입도 하나로 제한 |
| crash + lease expiry | deterministic clock으로 takeover를 재현하고 stale reservation owner의 commit을 차단 |
| provider attempt accounting | reservation 획득과 실제 provider 실행 횟수를 분리하고 실제 시작만 `provider_attempt_count`를 증가 |
| pre-provider failure exhaustion | provider 전 실패를 3회 이상 반복해도 quota가 소모되지 않고 이후 정상 provider 시작 가능 |
| bounded provider retry | 실제 provider 시작은 같은 canonical turn에서 최대 3회 |
| canonical commit | `GameSession.currentTurn`, latest `GameLog.stateVersion`, snapshot turn, mutation request result turn이 같은 값으로 수렴 |
| legacy production 형태 | legacy ledger/snapshot을 migration 후 현재 서버에서 복구 가능 |

## 동시성 테스트의 시간 정책

lease 만료 자체는 `MutableClock` 같은 deterministic clock으로 전진시켜 재현하며 `Thread.sleep`으로 시간을 맞추지 않습니다.

동시에, 테스트 실패가 CI 전체 job timeout까지 무한 대기하지 않도록 latch와 `Future#get()`에는 짧은 방어용 timeout을 둡니다. 이 timeout은 게임/lease 의미론을 결정하는 시간이 아니라 **테스트 hang을 빠른 실패로 바꾸는 안전장치**입니다.

#89 회귀에서 애플리케이션 `Clock`과 DB `CURRENT_TIMESTAMP`를 lease 판단에 섞어 사용하면 provider 진입 전에 실패한 뒤 latch가 풀리지 않을 수 있음을 확인했습니다. reservation lifecycle의 유효성/만료 판단은 주입된 application `Clock` 기준으로 통일했고, 해당 matrix가 CI에서 통과하는 것을 확인했습니다.

## provider exactly-once 경계

turn reservation은 유효 lease 동안 중복 provider 호출을 억제하고 canonical DB commit을 하나로 제한합니다. provider 호출 성공 직후 프로세스가 죽고 lease가 만료되면 takeover 요청이 provider를 다시 호출할 수 있습니다.

따라서 현재 보장은 다음과 같습니다.

- active lease 동안 duplicate provider suppression
- 실제 provider 시작 횟수의 turn당 bounded limit
- stale reservation owner의 canonical commit 차단
- canonical `GameSession` / `GameLog` / snapshot / completed request 결과의 단일 commit 수렴

외부 provider strict exactly-once는 보장하지 않습니다.

## 실패 진단

CI의 `Run PostgreSQL integration tests` step은 일반 backend test와 분리되어 있습니다. 실패 시 다음을 구분합니다.

1. container startup / Docker
2. Flyway migration 및 schema version
3. Hibernate schema validation
4. PostgreSQL unique / optimistic locking
5. idempotency key/fingerprint
6. reservation lease/owner/provider attempt
7. canonical state/log/snapshot/request 결과 불일치
8. concurrency test의 bounded wait timeout

Testcontainers와 Spring Boot가 container 및 datasource 초기화 로그를 남기며 production credential이나 application secret은 사용하지 않습니다.
